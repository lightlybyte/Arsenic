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

public class FrustumCuller {
    private final Frustum frustum = new Frustum();
    private final Map<Integer, ChunkBounds> allChunks = new ConcurrentHashMap<>();
    private final Set<Integer> visibleChunks = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Integer> chunkLODs = new ConcurrentHashMap<>();
    private final Map<Integer, Long> chunkLastVisible = new ConcurrentHashMap<>();
    private final Map<Integer, Float> chunkVisibilityScore = new ConcurrentHashMap<>();
    
    private volatile boolean useHierarchicalCulling = true;
    private volatile boolean useLODCulling = true;
    private volatile int maxLOD = 4;
    private volatile float lodDistanceScale = 1.0f;
    private volatile int viewDistance = 8;
    private volatile float farPlaneDistance = 100.0f;
    
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
    private volatile int[] lastLODDistribution = new int[5];
    
    private volatile boolean needsRebuild = true;
    private volatile int cachedTotalChunks = 0;
    
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    private static final int MIN_CHUNKS_PER_TASK = 16;
    private static final int MAX_CHUNKS_PER_TASK = 256;
    
    public void updateFrustum(Matrix4f projection, Matrix4f view) {
        frustum.update(projection, view);
        lastUpdateTime = System.nanoTime();
        this.farPlaneDistance = frustum.getFar();
    }
    
    public void updateFrustumFromClip(Matrix4f clip) {
        frustum.updateFromClip(clip);
        lastUpdateTime = System.nanoTime();
    }
    
    public Frustum getFrustum() {
        return frustum;
    }
    
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
        
        if (useHierarchicalCulling) {
            buildHierarchy();
        }
        
        System.out.println("[Arsenic] Rebuilt chunk list: " + cachedTotalChunks + " chunks");
    }
    
    public void addChunk(CullingTask.ChunkBounds chunk) {
        allChunks.put(chunk.index, new ChunkBounds(chunk));
        chunkLODs.put(chunk.index, 0);
        chunkVisibilityScore.put(chunk.index, 1.0f);
        cachedTotalChunks = allChunks.size();
        needsRebuild = true;
    }
    
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
    
    public CompletableFuture<Set<Integer>> cullAsync() {
        if (allChunks.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptySet());
        }
        
        if (isCullingInProgress) {
            return CompletableFuture.completedFuture(new HashSet<>(visibleChunks));
        }
        
        if (needsRebuild) {
            rebuildChunkList(new ArrayList<>(allChunks.values()).stream()
                .map(cb -> cb.toCullingTaskBounds())
                .toList());
        }
        
        isCullingInProgress = true;
        long startTime = System.nanoTime();
        
        visibleChunks.clear();
        visibleChunks.addAll(performCulling());
        
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        totalCullingTimeNanos.addAndGet(durationNanos);
        totalCullingCalls.incrementAndGet();
        
        lastCulledCount = visibleChunks.size();
        lastCullingTimeMs = durationNanos / 1_000_000;
        lastVisibilityRatio = cachedTotalChunks == 0 ? 0 : (float) visibleChunks.size() / cachedTotalChunks;
        updateLODStats();
        isCullingInProgress = false;
        
        return CompletableFuture.completedFuture(new HashSet<>(visibleChunks));
    }
    
    private Set<Integer> performCulling() {
        if (allChunks.isEmpty()) {
            return Collections.emptySet();
        }
        
        List<ChunkBounds> chunks = new ArrayList<>(allChunks.values());
        int chunkCount = chunks.size();
        int chunkSize = MathHelper.clamp(chunkCount / (THREADS * 2), MIN_CHUNKS_PER_TASK, MAX_CHUNKS_PER_TASK);
        int taskCount = (chunkCount + chunkSize - 1) / chunkSize;
        
        List<CullingTask> tasks = new ArrayList<>();
        for (int i = 0; i < chunkCount; i += chunkSize) {
            int end = Math.min(i + chunkSize, chunkCount);
            List<CullingTask.ChunkBounds> batch = new ArrayList<>();
            for (int j = i; j < end; j++) {
                batch.add(chunks.get(j).toCullingTaskBounds());
            }
            tasks.add(new CullingTask(frustum, batch, tasks.size()));
        }
        
        List<CullingTask.Result> results = new ArrayList<>();
        for (CullingTask task : tasks) {
            try {
                results.add(task.call());
            } catch (Exception e) {
                System.err.println("[Arsenic] Culling task failed: " + e.getMessage());
            }
        }
        
        Set<Integer> visible = new HashSet<>();
        for (CullingTask.Result result : results) {
            visible.addAll(result.visibleChunkIndices);
            totalChunksProcessed.addAndGet(result.processedCount);
            totalChunksVisible.addAndGet(result.visibleChunkIndices.size());
        }
        
        totalChunksCulled.addAndGet(chunkCount - visible.size());
        
        if (useLODCulling) {
            visible = applyLODCulling(visible);
        }
        
        long now = System.currentTimeMillis();
        for (int index : visible) {
            chunkLastVisible.put(index, now);
        }
        
        for (ChunkBounds chunk : chunks) {
            float score = chunkVisibilityScore.getOrDefault(chunk.index, 1.0f);
            boolean isVisible = visible.contains(chunk.index);
            score = score * 0.9f + (isVisible ? 0.1f : -0.05f);
            score = MathHelper.clamp(score, 0.0f, 1.0f);
            chunkVisibilityScore.put(chunk.index, score);
        }
        
        return visible;
    }
    
    private Set<Integer> applyLODCulling(Set<Integer> visible) {
        Set<Integer> result = new HashSet<>();
        float[] camPos = new float[]{0, 0, 0};
        
        for (int index : visible) {
            ChunkBounds chunk = allChunks.get(index);
            if (chunk == null) continue;
            
            float dist = MathHelper.distance(camPos[0], camPos[1], camPos[2],
                chunk.centerX, chunk.centerY, chunk.centerZ);
            
            int lod = frustum.getLOD(chunk.centerX, chunk.centerY, chunk.centerZ, maxLOD);
            chunkLODs.put(index, lod);
            
            float maxDist = farPlaneDistance / (1 + lod * 0.5f);
            if (dist < maxDist) {
                result.add(index);
            } else {
                totalLODCulls.incrementAndGet();
            }
        }
        
        return result;
    }
    
    private void buildHierarchy() {
        Map<Long, List<Integer>> parentMap = new HashMap<>();
        
        for (ChunkBounds chunk : allChunks.values()) {
            int parentX = chunk.chunkX / 8;
            int parentZ = chunk.chunkZ / 8;
            long key = ((long) parentX << 32) | (parentZ & 0xFFFFFFFFL);
            parentMap.computeIfAbsent(key, k -> new ArrayList<>()).add(chunk.index);
        }
        
        for (Map.Entry<Long, List<Integer>> entry : parentMap.entrySet()) {
            List<Integer> childIndices = entry.getValue();
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
    
    public Set<Integer> cullSync() {
        if (allChunks.isEmpty()) {
            return Collections.emptySet();
        }
        
        long startTime = System.nanoTime();
        visibleChunks.clear();
        
        for (ChunkBounds chunk : allChunks.values()) {
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
    
    public void forceUpdate() {
        if (allChunks.isEmpty()) return;
        cullSync();
    }
    
    public boolean isChunkVisible(int index) {
        return visibleChunks.contains(index);
    }
    
    public int getChunkLOD(int index) {
        return chunkLODs.getOrDefault(index, 0);
    }
    
    public float getChunkVisibilityScore(int index) {
        return chunkVisibilityScore.getOrDefault(index, 1.0f);
    }
    
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
    }
    
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