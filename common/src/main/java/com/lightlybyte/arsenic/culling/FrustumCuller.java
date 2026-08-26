package com.lightlybyte.arsenic.culling;

import com.lightlybyte.arsenic.math.Frustum;
import com.lightlybyte.arsenic.threading.ThreadManager;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main frustum culling manager.
 * Distributes culling work across multiple threads using Arsenic's ThreadManager.
 * 
 * This is the core of Arsenic's visibility determination system.
 * It determines which chunks are visible to the player and should be rendered.
 */
public class FrustumCuller {
    private final Frustum frustum = new Frustum();
    private final List<CullingTask.ChunkBounds> allChunks = new ArrayList<>();
    private final Set<Integer> visibleChunks = ConcurrentHashMap.newKeySet();
    
    // Metrics
    private final AtomicLong totalCullingTimeNanos = new AtomicLong(0);
    private final AtomicLong totalCullingCalls = new AtomicLong(0);
    private final AtomicLong totalChunksProcessed = new AtomicLong(0);
    private final AtomicLong totalChunksVisible = new AtomicLong(0);
    
    private volatile int lastCulledCount = 0;
    private volatile long lastCullingTimeMs = 0;
    private volatile boolean isCullingInProgress = false;
    private volatile long lastUpdateTime = 0;
    
    // Caching
    private volatile boolean needsRebuild = true;
    private volatile int cachedTotalChunks = 0;
    
    /**
     * Updates the frustum from the current camera matrices.
     * Should be called once per frame before culling.
     */
    public void updateFrustum(Matrix4f projection, Matrix4f view) {
        frustum.update(projection, view);
        lastUpdateTime = System.nanoTime();
    }
    
    /**
     * Gets the current frustum for direct use.
     */
    public Frustum getFrustum() {
        return frustum;
    }
    
    /**
     * Rebuilds the internal chunk list from the current world state.
     * This should be called when chunks load/unload or when the world changes.
     */
    public void rebuildChunkList(List<CullingTask.ChunkBounds> chunks) {
        synchronized (allChunks) {
            allChunks.clear();
            allChunks.addAll(chunks);
            cachedTotalChunks = allChunks.size();
            needsRebuild = false;
            visibleChunks.clear();
            
            System.out.println("[Arsenic] Rebuilt chunk list: " + cachedTotalChunks + " chunks");
        }
    }
    
    /**
     * Marks the chunk list as needing rebuild on the next cull.
     */
    public void markDirty() {
        needsRebuild = true;
    }
    
    /**
     * Checks if the chunk list needs rebuilding.
     */
    public boolean needsRebuild() {
        return needsRebuild;
    }
    
    /**
     * Performs multithreaded frustum culling on all registered chunks.
     * Uses HIGH priority tasks to ensure culling happens before rendering.
     * Returns a CompletableFuture with a set of visible chunk indices.
     */
    public CompletableFuture<Set<Integer>> cullAsync() {
        // Check if we have chunks to process
        if (allChunks.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptySet());
        }
        
        // If culling is already in progress, return the current visible set
        if (isCullingInProgress) {
            return CompletableFuture.completedFuture(new HashSet<>(visibleChunks));
        }
        
        isCullingInProgress = true;
        long startTime = System.nanoTime();
        
        // Clear previous results
        visibleChunks.clear();
        
        // Determine optimal task splitting
        int chunkCount = allChunks.size();
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        
        // Aim for ~100-200 chunks per task for optimal granularity
        int chunksPerTask = Math.max(50, chunkCount / (threadCount * 2));
        chunksPerTask = Math.min(chunksPerTask, 500); // Cap at 500 per task
        
        int taskCount = Math.max(1, (chunkCount + chunksPerTask - 1) / chunksPerTask);
        
        System.out.println("[Arsenic] Culling " + chunkCount + " chunks across " + 
                          taskCount + " tasks (" + chunksPerTask + " chunks/task)");
        
        // Create tasks
        List<CullingTask> tasks = new ArrayList<>();
        synchronized (allChunks) {
            for (int i = 0; i < chunkCount; i += chunksPerTask) {
                int end = Math.min(i + chunksPerTask, chunkCount);
                List<CullingTask.ChunkBounds> batch = allChunks.subList(i, end);
                
                // Create a copy of the batch to avoid concurrent modification issues
                List<CullingTask.ChunkBounds> batchCopy = new ArrayList<>(batch);
                CullingTask task = new CullingTask(frustum, batchCopy, tasks.size());
                tasks.add(task);
            }
        }
        
        // Submit all tasks with HIGH priority (culling is critical for rendering)
        List<CompletableFuture<CullingTask.Result>> futures = new ArrayList<>();
        ThreadManager tm = ThreadManager.getInstance();
        
        for (CullingTask task : tasks) {
            CompletableFuture<CullingTask.Result> future = tm.submitPriority(task, ThreadManager.TaskPriority.HIGH)
                .whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        System.err.println("[Arsenic] Culling task failed: " + throwable.getMessage());
                        throwable.printStackTrace();
                    } else if (result != null) {
                        // Add visible chunks to the set
                        visibleChunks.addAll(result.visibleChunkIndices);
                        
                        // Update metrics
                        totalChunksProcessed.addAndGet(result.processedCount);
                        totalChunksVisible.addAndGet(result.visibleChunkIndices.size());
                    }
                });
            futures.add(future);
        }
        
        // Wait for all tasks to complete and return results
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                long endTime = System.nanoTime();
                long durationNanos = endTime - startTime;
                totalCullingTimeNanos.addAndGet(durationNanos);
                totalCullingCalls.incrementAndGet();
                
                lastCulledCount = visibleChunks.size();
                lastCullingTimeMs = durationNanos / 1_000_000;
                isCullingInProgress = false;
                
                return new HashSet<>(visibleChunks);
            })
            .exceptionally(throwable -> {
                System.err.println("[Arsenic] Culling failed: " + throwable.getMessage());
                throwable.printStackTrace();
                isCullingInProgress = false;
                return Collections.emptySet();
            });
    }
    
    /**
     * Performs culling with an optional timeout.
     * If culling takes longer than the timeout, returns whatever results are available.
     */
    public CompletableFuture<Set<Integer>> cullAsyncWithTimeout(long timeout, TimeUnit unit) {
        return cullAsync().completeOnTimeout(
            new HashSet<>(visibleChunks),
            timeout,
            unit
        );
    }
    
    /**
     * Synchronous version for debugging or fallback.
     * This runs on the calling thread and blocks until complete.
     */
    public Set<Integer> cullSync() {
        if (allChunks.isEmpty()) {
            return Collections.emptySet();
        }
        
        long startTime = System.nanoTime();
        visibleChunks.clear();
        
        synchronized (allChunks) {
            for (CullingTask.ChunkBounds chunk : allChunks) {
                if (frustum.isBoxVisible(chunk.minX, chunk.minY, chunk.minZ,
                                         chunk.maxX, chunk.maxY, chunk.maxZ)) {
                    visibleChunks.add(chunk.index);
                }
            }
        }
        
        long endTime = System.nanoTime();
        lastCullingTimeMs = (endTime - startTime) / 1_000_000;
        lastCulledCount = visibleChunks.size();
        isCullingInProgress = false;
        
        return new HashSet<>(visibleChunks);
    }
    
    /**
     * Optimized culling for a single chunk.
     * Useful for quick checks without full batch processing.
     */
    public boolean isChunkVisible(CullingTask.ChunkBounds chunk) {
        return frustum.isBoxVisible(chunk.minX, chunk.minY, chunk.minZ,
                                    chunk.maxX, chunk.maxY, chunk.maxZ);
    }
    
    /**
     * Gets the current set of visible chunk indices.
     * Returns the last completed culling result.
     */
    public Set<Integer> getVisibleChunks() {
        return new HashSet<>(visibleChunks);
    }
    
    /**
     * Checks if a specific chunk index is visible.
     */
    public boolean isChunkVisible(int index) {
        return visibleChunks.contains(index);
    }
    
    // ==================== METRICS ====================
    
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
    
    public long getTotalCullingCalls() {
        return totalCullingCalls.get();
    }
    
    public long getTotalChunksProcessed() {
        return totalChunksProcessed.get();
    }
    
    public long getTotalChunksVisible() {
        return totalChunksVisible.get();
    }
    
    public double getAverageCullingTimeMs() {
        long calls = totalCullingCalls.get();
        if (calls == 0) return 0;
        return totalCullingTimeNanos.get() / (double) calls / 1_000_000.0;
    }
    
    public double getVisibilityRatio() {
        long processed = totalChunksProcessed.get();
        if (processed == 0) return 0;
        return totalChunksVisible.get() / (double) processed;
    }
    
    /**
     * Resets all metrics.
     */
    public void resetMetrics() {
        totalCullingTimeNanos.set(0);
        totalCullingCalls.set(0);
        totalChunksProcessed.set(0);
        totalChunksVisible.set(0);
        lastCullingTimeMs = 0;
        lastCulledCount = 0;
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Creates chunk bounds from a chunk's world coordinates.
     * Assumes a chunk is 16x16 blocks.
     */
    public static CullingTask.ChunkBounds createChunkBounds(int chunkX, int chunkZ, int index) {
        float minX = chunkX * 16;
        float minZ = chunkZ * 16;
        float maxX = minX + 16;
        float maxZ = minZ + 16;
        
        // Height - assume full world height for simplicity
        // In a real implementation, you'd use the actual chunk heightmap
        float minY = -64;
        float maxY = 320;
        
        return new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * Creates chunk bounds from a chunk's world coordinates with custom height.
     */
    public static CullingTask.ChunkBounds createChunkBounds(int chunkX, int chunkZ, int index,
                                                             float minY, float maxY) {
        float minX = chunkX * 16;
        float minZ = chunkZ * 16;
        float maxX = minX + 16;
        float maxZ = minZ + 16;
        
        return new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    // ==================== DEBUG/DIAGNOSTICS ====================
    
    /**
     * Returns a diagnostic string with current state.
     */
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Arsenic Frustum Culler Diagnostics ===\n");
        sb.append("Total chunks: ").append(cachedTotalChunks).append("\n");
        sb.append("Visible chunks: ").append(lastCulledCount).append("\n");
        sb.append("Visibility ratio: ").append(String.format("%.2f%%", 
            lastCulledCount * 100.0 / Math.max(1, cachedTotalChunks))).append("\n");
        sb.append("Last cull time: ").append(lastCullingTimeMs).append("ms\n");
        sb.append("Average cull time: ").append(String.format("%.2f", getAverageCullingTimeMs())).append("ms\n");
        sb.append("Total culling calls: ").append(totalCullingCalls.get()).append("\n");
        sb.append("Total chunks processed: ").append(totalChunksProcessed.get()).append("\n");
        sb.append("Total chunks visible: ").append(totalChunksVisible.get()).append("\n");
        sb.append("Culling in progress: ").append(isCullingInProgress).append("\n");
        sb.append("Needs rebuild: ").append(needsRebuild).append("\n");
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("FrustumCuller{chunks=%d, visible=%d, time=%dms}", 
            cachedTotalChunks, lastCulledCount, lastCullingTimeMs);
    }
}