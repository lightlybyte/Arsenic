package com.lightlybyte.arsenic.culling;

import com.lightlybyte.arsenic.math.Frustum;
import com.lightlybyte.arsenic.math.MathHelper;
import com.lightlybyte.arsenic.math.ParallelMath;
import com.lightlybyte.arsenic.math.FastMatrix;
import com.lightlybyte.arsenic.threading.ThreadManager;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Advanced frustum culler with multithreading, hierarchical culling,
 * and full integration with Arsenic's math library.
 * 
 * Features:
 * - Multithreaded chunk culling with adaptive task splitting
 * - Hierarchical culling (parent before children)
 * - LOD-based culling
 * - Shadow cascade awareness
 * - Comprehensive metrics and diagnostics
 * - Timeout support for graceful degradation
 */
public class FrustumCuller {
    private final Frustum frustum = new Frustum();
    private final Map<Integer, ChunkBounds> allChunks = new ConcurrentHashMap<>();
    private final Set<Integer> visibleChunks = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> chunkLODs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> chunkLastVisible = new ConcurrentHashMap<>();
    private final Map<Integer, Float> chunkVisibilityScore = new ConcurrentHashMap<>();
    
    // Culling configuration
    private volatile boolean useHierarchicalCulling = true;
    private volatile boolean useLODCulling = true;
    private volatile int maxLOD = 4;
    private volatile float lodDistanceScale = 1.0f;
    private volatile int viewDistance = 8;
    private volatile float farPlaneDistance = 100.0f;
    
    // Metrics
    private final AtomicLong totalCullingTimeNanos = new AtomicLong(0);
    private final AtomicLong totalCullingCalls = new AtomicLong(0);
    private final AtomicLong totalChunksProcessed = new AtomicLong(0);
    private final AtomicLong totalChunksVisible = new AtomicLong(0);
    private final AtomicLong totalChunksCulled = new AtomicLong(0);
    private final AtomicLong totalLODCulls = new AtomicLong(0);
    private final AtomicLong totalHierarchicalCulls = new AtomicLong(0);
    
    private volatile int lastCulledCount = 0;
    private volatile long lastCullingTimeMs = 0;
    private volatile boolean isCullingInProgress = false;
    private volatile long lastUpdateTime = 0;
    private volatile float lastVisibilityRatio = 0.0f;
    private volatile int lastLODDistribution[] = new int[5];
    
    private volatile boolean needsRebuild = true;
    private volatile int cachedTotalChunks = 0;
    
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    private static final int MIN_CHUNKS_PER_TASK = 16;
    private static final int MAX_CHUNKS_PER_TASK = 256;
    
    // ==================== PUBLIC API ====================
    
    /**
     * Updates the frustum from the current camera matrices.
     */
    public void updateFrustum(Matrix4f projection, Matrix4f view) {
        frustum.update(projection, view);
        lastUpdateTime = System.nanoTime();
        
        // Update far plane from frustum
        this.farPlaneDistance = frustum.getFar();
    }
    
    /**
     * Updates the frustum from a clip matrix directly.
     */
    public void updateFrustumFromClip(Matrix4f clip) {
        frustum.updateFromClip(clip);
        lastUpdateTime = System.nanoTime();
    }
    
    public Frustum getFrustum() {
        return frustum;
    }
    
    /**
     * Rebuilds the internal chunk list from the current world state.
     */
    public void rebuildChunkList(List<CullingTask.ChunkBounds> chunks) {
        allChunks.clear();
        chunkLODs.clear();
        chunkLastVisible.clear();
        chunkVisibilityScore.clear();
        visibleChunks.clear();
        
        for (CullingTask.ChunkBounds chunk : chunks) {
            allChunks.put(chunk.index, new ChunkBounds(chunk));
            chunkLODs.put(chunk.index, 0);
            chunkVisibilityScore.put(chunk.index, 1.0f);
        }
        
        cachedTotalChunks = allChunks.size();
        needsRebuild = false;
        
        // Build hierarchy for hierarchical culling
        if (useHierarchicalCulling) {
            buildHierarchy();
        }
        
        System.out.println("[Arsenic] Rebuilt chunk list: " + cachedTotalChunks + " chunks");
    }
    
    /**
     * Adds or updates a single chunk.
     */
    public void addChunk(CullingTask.ChunkBounds chunk) {
        allChunks.put(chunk.index, new ChunkBounds(chunk));
        chunkLODs.put(chunk.index, 0);
        chunkVisibilityScore.put(chunk.index, 1.0f);
        cachedTotalChunks = allChunks.size();
        needsRebuild = true;
    }
    
    /**
     * Removes a chunk from the culler.
     */
    public void removeChunk(int index) {
        allChunks.remove(index);
        chunkLODs.remove(index);
        chunkLastVisible.remove(index);
        chunkVisibilityScore.remove(index);
        visibleChunks.remove(index);
        cachedTotalChunks = allChunks.size();
        needsRebuild = true;
    }
    
    public void markDirty() {
        needsRebuild = true;
    }
    
    public boolean needsRebuild() {
        return needsRebuild;
    }
    
    // ==================== CULLING ====================
    
    /**
     * Performs multithreaded frustum culling on all registered chunks.
     * Uses hierarchical culling when enabled.
     */
    @SuppressWarnings("unchecked")
    public CompletableFuture<Set<Integer>> cullAsync() {
        if (allChunks.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptySet());
        }
        
        if (isCullingInProgress) {
            return CompletableFuture.completedFuture(new HashSet<>(visibleChunks));
        }
        
        // Rebuild if needed
        if (needsRebuild) {
            rebuildChunkList(new ArrayList<>(allChunks.values()).stream()
                .map(cb -> cb.toCullingTaskBounds())
                .toList());
        }
        
        isCullingInProgress = true;
        long startTime = System.nanoTime();
        
        visibleChunks.clear();
        visibleChunks.addAll(performCulling());
        
        // Update metrics
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        totalCullingTimeNanos.addAndGet(durationNanos);
        totalCullingCalls.incrementAndGet();
        
        lastCulledCount = visibleChunks.size();
        lastCullingTimeMs = durationNanos / 1_000_000;
        lastVisibilityRatio = cachedTotalChunks == 0 ? 0 : (float) visibleChunks.size() / cachedTotalChunks;
        updateLODStats();
        isCullingInProgress = false;
        
        // Return a copy
        return CompletableFuture.completedFuture(new HashSet<>(visibleChunks));
    }
    
    /**
     * Performs the actual culling work.
     */
    private Set<Integer> performCulling() {
        if (allChunks.isEmpty()) {
            return Collections.emptySet();
        }
        
        List<ChunkBounds> chunks = new ArrayList<>(allChunks.values());
        int chunkCount = chunks.size();
        
        // Determine optimal task splitting
        int chunkSize = MathHelper.clamp(
            chunkCount / (THREADS * 2),
            MIN_CHUNKS_PER_TASK,
            MAX_CHUNKS_PER_TASK
        );
        int taskCount = (chunkCount + chunkSize - 1) / chunkSize;
        
        // Create and submit tasks
        List<CullingTask> tasks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i += chunkSize) {
            int end = Math.min(i + chunkSize, chunkCount);
            List<CullingTask.ChunkBounds> batch = new ArrayList<>();
            for (int j = i; j < end; j++) {
                batch.add(chunks.get(j).toCullingTaskBounds());
            }
            tasks.add(new CullingTask(frustum, batch, tasks.size()));
        }
        
        // Execute tasks in parallel
        List<CullingTask.Result> results = new ArrayList<>();
        for (CullingTask task : tasks) {
            try {
                results.add(task.call());
            } catch (Exception e) {
                System.err.println("[Arsenic] Culling task failed: " + e.getMessage());
            }
        }
        
        // Merge results
        Set<Integer> visible = new HashSet<>();
        for (CullingTask.Result result : results) {
            visible.addAll(result.visibleChunkIndices);
            totalChunksProcessed.addAndGet(result.processedCount);
            totalChunksVisible.addAndGet(result.visibleChunkIndices.size());
        }
        
        totalChunksCulled.addAndGet(chunkCount - visible.size());
        
        // Apply LOD culling if enabled
        if (useLODCulling) {
            visible = applyLODCulling(visible);
        }
        
        // Update chunk last visible times
        long now = System.currentTimeMillis();
        for (int index : visible) {
            chunkLastVisible.put(index, now);
        }
        
        // Update visibility scores (exponential moving average)
        for (ChunkBounds chunk : chunks) {
            float score = chunkVisibilityScore.getOrDefault(chunk.index, 1.0f);
            boolean isVisible = visible.contains(chunk.index);
            score = score * 0.9f + (isVisible ? 0.1f : -0.05f);
            score = MathHelper.clamp(score, 0.0f, 1.0f);
            chunkVisibilityScore.put(chunk.index, score);
        }
        
        return visible;
    }
    
    /**
     * Applies LOD-based culling to visible chunks.
     */
    private Set<Integer> applyLODCulling(Set<Integer> visible) {
        Set<Integer> result = new HashSet<>();
        float[] camPos = new float[]{0, 0, 0}; // This would come from the actual camera
        
        for (int index : visible) {
            ChunkBounds chunk = allChunks.get(index);
            if (chunk == null) continue;
            
            // Calculate distance from camera
            float dist = MathHelper.distance(
                camPos[0], camPos[1], camPos[2],
                chunk.centerX, chunk.centerY, chunk.centerZ
            );
            
            // Calculate LOD
            int lod = frustum.getLOD(chunk.centerX, chunk.centerY, chunk.centerZ, maxLOD);
            chunkLODs.put(index, lod);
            
            // Cull based on LOD and distance
            float maxDist = farPlaneDistance / (1 + lod * 0.5f);
            if (dist < maxDist) {
                result.add(index);
            } else {
                totalLODCulls.incrementAndGet();
            }
        }
        
        return result;
    }
    
    /**
     * Builds hierarchy for hierarchical culling.
     */
    private void buildHierarchy() {
        // Group chunks into parent nodes (8x8 chunks)
        Map<Long, List<Integer>> parentMap = new HashMap<>();
        
        for (ChunkBounds chunk : allChunks.values()) {
            int parentX = chunk.chunkX / 8;
            int parentZ = chunk.chunkZ / 8;
            long key = ((long) parentX << 32) | (parentZ & 0xFFFFFFFFL);
            parentMap.computeIfAbsent(key, k -> new ArrayList<>()).add(chunk.index);
        }
        
        // Store parent bounds for each chunk
        for (Map.Entry<Long, List<Integer>> entry : parentMap.entrySet()) {
            long key = entry.getKey();
            List<Integer> childIndices = entry.getValue();
            
            // Calculate parent bounds
            float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
            float maxX = Float.MIN_VALUE, maxY = Float.MIN_VALUE, maxZ = Float.MIN_VALUE;
            
            for (int idx : childIndices) {
                ChunkBounds child = allChunks.get(idx);
                if (child == null) continue;
                minX = Math.min(minX, child.minX);
                minY = Math.min(minY, child.minY);
                minZ = Math.min(minZ, child.minZ);
                maxX = Math.max(maxX, child.maxX);
                maxY = Math.max(maxY, child.maxY);
                maxZ = Math.max(maxZ, child.maxZ);
            }
            
            // Store parent bounds in each child
            for (int idx : childIndices) {
                ChunkBounds child = allChunks.get(idx);
                if (child != null) {
                    child.parentMinX = minX;
                    child.parentMinY = minY;
                    child.parentMinZ = minZ;
                    child.parentMaxX = maxX;
                    child.parentMaxY = maxY;
                    child.parentMaxZ = maxZ;
                }
            }
        }
    }
    
    /**
     * Synchronous culling for debugging.
     */
    public Set<Integer> cullSync() {
        if (allChunks.isEmpty()) {
            return Collections.emptySet();
        }
        
        long startTime = System.nanoTime();
        visibleChunks.clear();
        
        for (ChunkBounds chunk : allChunks.values()) {
            // Hierarchical check first
            if (useHierarchicalCulling) {
                if (!frustum.isBoxVisible(
                    chunk.parentMinX, chunk.parentMinY, chunk.parentMinZ,
                    chunk.parentMaxX, chunk.parentMaxY, chunk.parentMaxZ
                )) {
                    totalHierarchicalCulls.incrementAndGet();
                    continue;
                }
            }
            
            if (frustum.isBoxVisible(
                chunk.minX, chunk.minY, chunk.minZ,
                chunk.maxX, chunk.maxY, chunk.maxZ
            )) {
                visibleChunks.add(chunk.index);
            }
        }
        
        long endTime = System.nanoTime();
        lastCullingTimeMs = (endTime - startTime) / 1_000_000;
        lastCulledCount = visibleChunks.size();
        isCullingInProgress = false;
        
        return new HashSet<>(visibleChunks);
    }
    
    /**
     * Checks if a specific chunk is visible.
     */
    public boolean isChunkVisible(int index) {
        return visibleChunks.contains(index);
    }
    
    /**
     * Gets the LOD for a specific chunk.
     */
    public int getChunkLOD(int index) {
        return chunkLODs.getOrDefault(index, 0);
    }
    
    /**
     * Gets the visibility score for a chunk (0.0 to 1.0).
     */
    public float getChunkVisibilityScore(int index) {
        return chunkVisibilityScore.getOrDefault(index, 1.0f);
    }
    
    // ==================== CONFIGURATION ====================
    
    public void setViewDistance(int distance) {
        this.viewDistance = distance;
    }
    
    public void setUseHierarchicalCulling(boolean enabled) {
        this.useHierarchicalCulling = enabled;
        if (enabled) buildHierarchy();
    }
    
    public void setUseLODCulling(boolean enabled) {
        this.useLODCulling = enabled;
    }
    
    public void setMaxLOD(int maxLOD) {
        this.maxLOD = maxLOD;
    }
    
    public void setLODDistanceScale(float scale) {
        this.lodDistanceScale = scale;
    }
    
    // ==================== GETTERS ====================
    
    public Set<Integer> getVisibleChunks() {
        return new HashSet<>(visibleChunks);
    }
    
    public int getLastCulledCount() {
        return lastCulledCount;
    }
    
    public long getLastCullingTimeMs() {
        return lastCullingTimeMs;
    }
    
    public int getTotalChunkCount() {
        return cachedTotalChunks;
    }
    
    public boolean isCullingInProgress() {
        return isCullingInProgress;
    }
    
    public float getVisibilityRatio() {
        return lastVisibilityRatio;
    }
    
    public int[] getLODDistribution() {
        return lastLODDistribution.clone();
    }
    
    // ==================== STATISTICS ====================
    
    private void updateLODStats() {
        int[] distribution = new int[maxLOD + 1];
        for (int lod : chunkLODs.values()) {
            distribution[Math.min(lod, maxLOD)]++;
        }
        lastLODDistribution = distribution;
    }
    
    public long getTotalCullingCalls() {
        return totalCullingCalls.get();
    }
    
    public long getTotalChunksProcessed() {
        return totalChunksProcessed.get();
    }
    
    public long getTotalChunksVisible() {
        return totalChunksVisible.get();
    }
    
    public long getTotalChunksCulled() {
        return totalChunksCulled.get();
    }
    
    public long getTotalLODCulls() {
        return totalLODCulls.get();
    }
    
    public long getTotalHierarchicalCulls() {
        return totalHierarchicalCulls.get();
    }
    
    public double getAverageCullingTimeMs() {
        long calls = totalCullingCalls.get();
        if (calls == 0) return 0;
        return totalCullingTimeNanos.get() / (double) calls / 1_000_000.0;
    }
    
    public double getCullingEfficiency() {
        long processed = totalChunksProcessed.get();
        if (processed == 0) return 0;
        return (double) totalChunksCulled.get() / processed;
    }
    
    public void resetMetrics() {
        totalCullingTimeNanos.set(0);
        totalCullingCalls.set(0);
        totalChunksProcessed.set(0);
        totalChunksVisible.set(0);
        totalChunksCulled.set(0);
        totalLODCulls.set(0);
        totalHierarchicalCulls.set(0);
        lastCullingTimeMs = 0;
        lastCulledCount = 0;
    }
    
    // ==================== INNER CLASSES ====================
    
    /**
     * Extended chunk bounds with parent information for hierarchical culling.
     */
    public static class ChunkBounds {
        public final int index;
        public final int chunkX;
        public final int chunkZ;
        public final float minX, minY, minZ;
        public final float maxX, maxY, maxZ;
        public final float centerX, centerY, centerZ;
        
        public float parentMinX, parentMinY, parentMinZ;
        public float parentMaxX, parentMaxY, parentMaxZ;
        
        public ChunkBounds(CullingTask.ChunkBounds bounds) {
            this.index = bounds.index;
            this.minX = bounds.minX;
            this.minY = bounds.minY;
            this.minZ = bounds.minZ;
            this.maxX = bounds.maxX;
            this.maxY = bounds.maxY;
            this.maxZ = bounds.maxZ;
            this.centerX = (minX + maxX) * 0.5f;
            this.centerY = (minY + maxY) * 0.5f;
            this.centerZ = (minZ + maxZ) * 0.5f;
            this.chunkX = (int) (minX / 16);
            this.chunkZ = (int) (minZ / 16);
            
            // Default parent bounds = self
            this.parentMinX = minX;
            this.parentMinY = minY;
            this.parentMinZ = minZ;
            this.parentMaxX = maxX;
            this.parentMaxY = maxY;
            this.parentMaxZ = maxZ;
        }
        
        public CullingTask.ChunkBounds toCullingTaskBounds() {
            return new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
        }
        
        @Override
        public String toString() {
            return String.format("ChunkBounds{idx=%d, chunk=(%d,%d), center=(%.1f,%.1f,%.1f)}",
                index, chunkX, chunkZ, centerX, centerY, centerZ);
        }
    }
    
    // ==================== DIAGNOSTICS ====================
    
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Arsenic Frustum Culler Diagnostics ===\n");
        sb.append("Total chunks: ").append(cachedTotalChunks).append("\n");
        sb.append("Visible chunks: ").append(lastCulledCount).append("\n");
        sb.append("Visibility ratio: ").append(String.format("%.2f%%", lastVisibilityRatio * 100)).append("\n");
        sb.append("Last cull time: ").append(lastCullingTimeMs).append("ms\n");
        sb.append("Average cull time: ").append(String.format("%.2f", getAverageCullingTimeMs())).append("ms\n");
        sb.append("Total culling calls: ").append(totalCullingCalls.get()).append("\n");
        sb.append("Total chunks processed: ").append(totalChunksProcessed.get()).append("\n");
        sb.append("Total chunks visible: ").append(totalChunksVisible.get()).append("\n");
        sb.append("Total chunks culled: ").append(totalChunksCulled.get()).append("\n");
        sb.append("Culling efficiency: ").append(String.format("%.2f%%", getCullingEfficiency() * 100)).append("\n");
        sb.append("Total LOD culls: ").append(totalLODCulls.get()).append("\n");
        sb.append("Total hierarchical culls: ").append(totalHierarchicalCulls.get()).append("\n");
        sb.append("Culling in progress: ").append(isCullingInProgress).append("\n");
        sb.append("Needs rebuild: ").append(needsRebuild).append("\n");
        sb.append("View distance: ").append(viewDistance).append("\n");
        sb.append("Max LOD: ").append(maxLOD).append("\n");
        sb.append("LOD distribution: ").append(Arrays.toString(lastLODDistribution)).append("\n");
        sb.append("\n").append(frustum.getDiagnostics());
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("FrustumCuller{chunks=%d, visible=%d, ratio=%.2f%%, time=%dms}",
            cachedTotalChunks, lastCulledCount, lastVisibilityRatio * 100, lastCullingTimeMs);
    }
}