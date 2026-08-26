package com.lightlybyte.arsenic.culling;

import com.lightlybyte.arsenic.math.FastMatrix;
import com.lightlybyte.arsenic.math.MathHelper;
import com.lightlybyte.arsenic.math.ParallelMath;
import com.lightlybyte.arsenic.math.Frustrum;
import com.lightlybyte.arsenic.threading.ThreadManager;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main frustum culling manager.
 * Distributes culling work across multiple threads using Arsenic's ThreadManager.
 * 
 * Frustum culling is the process of determining which chunks are visible to the
 * player's camera and should be rendered, and which are not.
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
    
    public Frustum getFrustum() {
        return frustum;
    }
    
    /**
     * Rebuilds the internal chunk list from the current world state.
     */
    public void rebuildChunkList(List<CullingTask.ChunkBounds> chunks) {
        synchronized (allChunks) {
            allChunks.clear();
            allChunks.addAll(chunks);
            cachedTotalChunks = allChunks.size();
            needsRebuild = false;
            visibleChunks.clear();
        }
    }
    
    public void markDirty() {
        needsRebuild = true;
    }
    
    public boolean needsRebuild() {
        return needsRebuild;
    }
    
    /**
     * Performs multithreaded frustum culling on all registered chunks.
     * Uses HIGH priority tasks to ensure culling happens before rendering.
     */
    public CompletableFuture<Set<Integer>> cullAsync() {
        // If no chunks, return empty set
        if (allChunks.isEmpty()) {
            return CompletableFuture.completedFuture(Collections.emptySet());
        }
        
        // If culling is already in progress, return current visible set
        if (isCullingInProgress) {
            return CompletableFuture.completedFuture(new HashSet<>(visibleChunks));
        }
        
        isCullingInProgress = true;
        long startTime = System.nanoTime();
        
        visibleChunks.clear();
        
        int chunkCount = allChunks.size();
        int threadCount = Math.min(Runtime.getRuntime().availableProcessors(), 8);
        int chunksPerTask = Math.max(50, chunkCount / (threadCount * 2));
        chunksPerTask = Math.min(chunksPerTask, 500);
        
        List<CullingTask> tasks = new ArrayList<>();
        synchronized (allChunks) {
            for (int i = 0; i < chunkCount; i += chunksPerTask) {
                int end = Math.min(i + chunksPerTask, chunkCount);
                List<CullingTask.ChunkBounds> batch = allChunks.subList(i, end);
                List<CullingTask.ChunkBounds> batchCopy = new ArrayList<>(batch);
                CullingTask task = new CullingTask(frustum, batchCopy, tasks.size());
                tasks.add(task);
            }
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        ThreadManager tm = ThreadManager.getInstance();
        
        for (CullingTask task : tasks) {
            Runnable wrappedTask = () -> {
                try {
                    CullingTask.Result result = task.call();
                    if (result != null) {
                        visibleChunks.addAll(result.visibleChunkIndices);
                        totalChunksProcessed.addAndGet(result.processedCount);
                        totalChunksVisible.addAndGet(result.visibleChunkIndices.size());
                    }
                } catch (Exception e) {
                    System.err.println("[Arsenic] Culling task failed: " + e.getMessage());
                    e.printStackTrace();
                }
            };
            
            CompletableFuture<Void> future = tm.submitPriority(wrappedTask, ThreadManager.TaskPriority.HIGH);
            futures.add(future);
        }
        
        // Wait for all tasks to complete
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        
        // Return a future that completes with the visible chunks
        CompletableFuture<Set<Integer>> resultFuture = allDone.thenApply(v -> {
            long endTime = System.nanoTime();
            long durationNanos = endTime - startTime;
            totalCullingTimeNanos.addAndGet(durationNanos);
            totalCullingCalls.incrementAndGet();
            
            lastCulledCount = visibleChunks.size();
            lastCullingTimeMs = durationNanos / 1_000_000;
            isCullingInProgress = false;
            
            return new HashSet<>(visibleChunks);
        });
        
        // Handle errors
        return resultFuture.handle((result, throwable) -> {
            if (throwable != null) {
                System.err.println("[Arsenic] Culling failed: " + throwable.getMessage());
                throwable.printStackTrace();
                isCullingInProgress = false;
                return new HashSet<Integer>();
            }
            return result;
        });
    }
    
    /**
     * Performs culling with a timeout.
     */
    public CompletableFuture<Set<Integer>> cullAsyncWithTimeout(long timeout, TimeUnit unit) {
        CompletableFuture<Set<Integer>> future = cullAsync();
        try {
            return future.completeOnTimeout(
                new HashSet<>(visibleChunks),
                timeout,
                unit
            );
        } catch (Exception e) {
            // completeOnTimeout might not be available in older Java versions
            // Fallback: use a timer
            CompletableFuture<Set<Integer>> timeoutFuture = new CompletableFuture<>();
            ThreadManager.getInstance().schedule(() -> {
                if (!future.isDone()) {
                    timeoutFuture.complete(new HashSet<>(visibleChunks));
                }
            }, timeout, unit);
            
            return future.applyToEither(timeoutFuture, result -> result);
        }
    }
    
    /**
     * Synchronous version for debugging.
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
     * Tests a single chunk against the frustum.
     */
    public boolean isChunkVisible(CullingTask.ChunkBounds chunk) {
        return frustum.isBoxVisible(chunk.minX, chunk.minY, chunk.minZ,
                                    chunk.maxX, chunk.maxY, chunk.maxZ);
    }
    
    /**
     * Gets the current set of visible chunks.
     */
    public Set<Integer> getVisibleChunks() {
        return new HashSet<>(visibleChunks);
    }
    
    /**
     * Checks if a chunk index is visible.
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
    
    public void resetMetrics() {
        totalCullingTimeNanos.set(0);
        totalCullingCalls.set(0);
        totalChunksProcessed.set(0);
        totalChunksVisible.set(0);
        lastCullingTimeMs = 0;
        lastCulledCount = 0;
    }
    
    // ==================== UTILITY ====================
    
    public static CullingTask.ChunkBounds createChunkBounds(int chunkX, int chunkZ, int index) {
        float minX = chunkX * 16;
        float minZ = chunkZ * 16;
        float maxX = minX + 16;
        float maxZ = minZ + 16;
        float minY = -64;
        float maxY = 320;
        return new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    public static CullingTask.ChunkBounds createChunkBounds(int chunkX, int chunkZ, int index,
                                                             float minY, float maxY) {
        float minX = chunkX * 16;
        float minZ = chunkZ * 16;
        float maxX = minX + 16;
        float maxZ = minZ + 16;
        return new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
    }
    
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