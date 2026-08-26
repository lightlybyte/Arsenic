package com.lightlybyte.arsenic.culling;

import com.lightlybyte.arsenic.math.Frustum;
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
 */
public class FrustumCuller {
    private final Frustum frustum = new Frustum();
    private final List<CullingTask.ChunkBounds> allChunks = new ArrayList<>();
    private final Set<Integer> visibleChunks = ConcurrentHashMap.newKeySet();
    
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
    
    public void updateFrustum(Matrix4f projection, Matrix4f view) {
        frustum.update(projection, view);
        lastUpdateTime = System.nanoTime();
    }
    
    public Frustum getFrustum() {
        return frustum;
    }
    
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
    
    public CompletableFuture<Set<Integer>> cullAsync() {
        if (allChunks.isEmpty()) {
            return CompletableFuture.completedFuture(new HashSet<>());
        }
        
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
            // Wrap the task in a Runnable since submitPriority expects Runnable
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
                return new HashSet<>();
            });
    }
    
    public CompletableFuture<Set<Integer>> cullAsyncWithTimeout(long timeout, TimeUnit unit) {
        return cullAsync().completeOnTimeout(
            new HashSet<>(visibleChunks),
            timeout,
            unit
        );
    }
    
    public Set<Integer> cullSync() {
        if (allChunks.isEmpty()) {
            return new HashSet<>();
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
    
    public boolean isChunkVisible(CullingTask.ChunkBounds chunk) {
        return frustum.isBoxVisible(chunk.minX, chunk.minY, chunk.minZ,
                                    chunk.maxX, chunk.maxY, chunk.maxZ);
    }
    
    public Set<Integer> getVisibleChunks() {
        return new HashSet<>(visibleChunks);
    }
    
    public boolean isChunkVisible(int index) {
        return visibleChunks.contains(index);
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