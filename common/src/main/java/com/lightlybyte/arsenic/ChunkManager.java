package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.culling.CullingTask;
import com.lightlybyte.arsenic.culling.FrustumCuller;
import com.lightlybyte.arsenic.threading.ThreadManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ChunkManager {
    private static final ChunkManager INSTANCE = new ChunkManager();
    
    private final FrustumCuller culler = new FrustumCuller();
    private final Map<Long, CullingTask.ChunkBounds> chunkBoundsMap = new ConcurrentHashMap<>();
    private final Map<Long, Long> chunkLoadTime = new ConcurrentHashMap<>();
    
    private volatile boolean isInitialized = false;
    private volatile int viewDistance = 8;
    private volatile int totalChunksLoaded = 0;
    private volatile int totalChunksUnloaded = 0;
    private volatile long lastRebuildTime = 0;
    private volatile boolean needsRebuild = true;
    
    private final AtomicInteger chunkIdCounter = new AtomicInteger(0);
    
    private ChunkManager() {}
    
    public static ChunkManager getInstance() {
        return INSTANCE;
    }
    
    public void initialize() {
        if (isInitialized) return;
        Arsenic.getLogger().info("Initializing ChunkManager...");
        isInitialized = true;
        ThreadManager.getInstance().scheduleAtFixedRate(
            this::rebuildIfNeeded, 5, 5, java.util.concurrent.TimeUnit.SECONDS
        );
        Arsenic.getLogger().info("ChunkManager initialized!");
    }
    
    public void addChunkBounds(int index, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        long key = (long) index;
        CullingTask.ChunkBounds bounds = new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
        chunkBoundsMap.put(key, bounds);
        chunkLoadTime.put(key, System.currentTimeMillis());
        totalChunksLoaded++;
        culler.addChunk(bounds);
        culler.markDirty();
        needsRebuild = true;
    }
    
    public void removeChunkBounds(int index) {
        long key = (long) index;
        chunkBoundsMap.remove(key);
        chunkLoadTime.remove(key);
        totalChunksUnloaded++;
        culler.removeChunk(index);
        culler.markDirty();
        needsRebuild = true;
    }
    
    public void setViewDistance(int distance) {
        this.viewDistance = distance;
        culler.markDirty();
        needsRebuild = true;
        Arsenic.getLogger().info("View distance set to: {}", distance);
    }
    
    public void rebuildIfNeeded() {
        if (!needsRebuild && !culler.needsRebuild()) return;
        long startTime = System.nanoTime();
        List<CullingTask.ChunkBounds> chunks = new ArrayList<>(chunkBoundsMap.values());
        culler.rebuildChunkList(chunks);
        needsRebuild = false;
        lastRebuildTime = System.nanoTime() - startTime;
    }
    
    public void forceRebuild() {
        needsRebuild = true;
        rebuildIfNeeded();
    }
    
    public FrustumCuller getCuller() {
        return culler;
    }
    
    public int getChunkCount() {
        return chunkBoundsMap.size();
    }
    
    public int getTotalChunksLoaded() {
        return totalChunksLoaded;
    }
    
    public int getTotalChunksUnloaded() {
        return totalChunksUnloaded;
    }
    
    public int getViewDistance() {
        return viewDistance;
    }
    
    public long getLastRebuildTime() {
        return lastRebuildTime;
    }
    
    public boolean needsRebuild() {
        return needsRebuild || culler.needsRebuild();
    }
    
    public boolean isChunkLoaded(int index) {
        return chunkBoundsMap.containsKey((long) index);
    }
    
    public CullingTask.ChunkBounds getChunkBounds(int index) {
        return chunkBoundsMap.get((long) index);
    }
    
    public Collection<CullingTask.ChunkBounds> getAllChunks() {
        return new ArrayList<>(chunkBoundsMap.values());
    }
    
    public Set<Integer> getVisibleChunks() {
        return culler.getVisibleChunks();
    }
    
    public boolean isChunkVisible(int index) {
        return culler.isChunkVisible(index);
    }
    
    public void resetStats() {
        totalChunksLoaded = 0;
        totalChunksUnloaded = 0;
        chunkLoadTime.clear();
        culler.resetMetrics();
    }
    
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Arsenic ChunkManager Diagnostics ===\n");
        sb.append("Initialized: ").append(isInitialized).append("\n");
        sb.append("Loaded chunks: ").append(chunkBoundsMap.size()).append("\n");
        sb.append("Total loaded: ").append(totalChunksLoaded).append("\n");
        sb.append("Total unloaded: ").append(totalChunksUnloaded).append("\n");
        sb.append("View distance: ").append(viewDistance).append("\n");
        sb.append("Needs rebuild: ").append(needsRebuild).append("\n");
        sb.append("Last rebuild: ").append(lastRebuildTime).append("ns\n\n");
        sb.append(culler.getDiagnostics());
        return sb.toString();
    }
}