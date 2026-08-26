package com.lightlybyte.arsenic.culling;

import com.lightlybyte.arsenic.math.Frustum;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A single culling task that processes a batch of chunks.
 * This is designed to be run on a thread pool.
 */
public class CullingTask implements Callable<CullingTask.Result> {
    private final Frustum frustum;
    private final List<ChunkBounds> chunks;
    private final int taskId;
    
    public static class Result {
        public final List<Integer> visibleChunkIndices;
        public final int processedCount;
        public final long processingTimeNanos;
        
        public Result(List<Integer> visibleChunkIndices, int processedCount, long processingTimeNanos) {
            this.visibleChunkIndices = visibleChunkIndices;
            this.processedCount = processedCount;
            this.processingTimeNanos = processingTimeNanos;
        }
    }
    
    public static class ChunkBounds {
        public final int index;
        public final float minX, minY, minZ;
        public final float maxX, maxY, maxZ;
        
        public ChunkBounds(int index, float minX, float minY, float minZ, 
                           float maxX, float maxY, float maxZ) {
            this.index = index;
            this.minX = minX;
            this.minY = minY;
            this.minZ = minZ;
            this.maxX = maxX;
            this.maxY = maxY;
            this.maxZ = maxZ;
        }
    }
    
    public CullingTask(Frustum frustum, List<ChunkBounds> chunks, int taskId) {
        this.frustum = frustum;
        this.chunks = chunks;
        this.taskId = taskId;
    }
    
    @Override
    public Result call() {
        long startTime = System.nanoTime();
        List<Integer> visible = new ArrayList<>(chunks.size());
        
        for (ChunkBounds chunk : chunks) {
            if (frustum.isBoxVisible(chunk.minX, chunk.minY, chunk.minZ,
                                     chunk.maxX, chunk.maxY, chunk.maxZ)) {
                visible.add(chunk.index);
            }
        }
        
        long endTime = System.nanoTime();
        return new Result(visible, chunks.size(), endTime - startTime);
    }
}