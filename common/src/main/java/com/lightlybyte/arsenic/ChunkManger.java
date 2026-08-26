package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.culling.CullingTask;
import com.lightlybyte.arsenic.culling.FrustumCuller;
import com.lightlybyte.arsenic.threading.ThreadManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the chunk list for frustum culling.
 * Tracks loaded chunks and updates the culler when chunks change.
 */
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
    
    /**
     * Initializes the chunk manager.
     */
    public void initialize() {
        if (isInitialized) return;
        
        Arsenic.getLogger().info("Initializing ChunkManager...");
        isInitialized = true;
        
        // Start periodic rebuild check
        ThreadManager.getInstance().scheduleAtFixedRate(
            this::rebuildIfNeeded,
            5, 5, java.util.concurrent.TimeUnit.SECONDS
        );
        
        Arsenic.getLogger().info("ChunkManager initialized!");
    }
    
    /**
     * Called when a chunk is loaded.
     */
    public void onChunkLoaded(Chunk chunk) {
        if (!isInitialized || chunk == null) return;
        
        try {
            long key = getChunkKey(chunk.getPos().x, chunk.getPos().z);
            
            // Check if already loaded
            if (chunkBoundsMap.containsKey(key)) {
                return;
            }
            
            CullingTask.ChunkBounds bounds = createBoundsFromChunk(chunk);
            chunkBoundsMap.put(key, bounds);
            chunkLoadTime.put(key, System.currentTimeMillis());
            totalChunksLoaded++;
            
            // Add to culler
            culler.addChunk(bounds);
            culler.markDirty();
            needsRebuild = true;
            
            if (Arsenic.getLogger().isDebugEnabled()) {
                Arsenic.getLogger().debug("Chunk loaded: {}, {} (total: {})", 
                    chunk.getPos().x, chunk.getPos().z, chunkBoundsMap.size());
            }
            
        } catch (Exception e) {
            Arsenic.getLogger().warn("Failed to load chunk: {}", e.getMessage());
        }
    }
    
    /**
     * Called when a chunk is unloaded.
     */
    public void onChunkUnloaded(Chunk chunk) {
        if (!isInitialized || chunk == null) return;
        
        try {
            long key = getChunkKey(chunk.getPos().x, chunk.getPos().z);
            
            if (!chunkBoundsMap.containsKey(key)) {
                return;
            }
            
            int index = chunk.getPos().x * 1000000 + chunk.getPos().z;
            chunkBoundsMap.remove(key);
            chunkLoadTime.remove(key);
            totalChunksUnloaded++;
            
            // Remove from culler
            culler.removeChunk(index);
            culler.markDirty();
            needsRebuild = true;
            
            if (Arsenic.getLogger().isDebugEnabled()) {
                Arsenic.getLogger().debug("Chunk unloaded: {}, {} (total: {})", 
                    chunk.getPos().x, chunk.getPos().z, chunkBoundsMap.size());
            }
            
        } catch (Exception e) {
            Arsenic.getLogger().warn("Failed to unload chunk: {}", e.getMessage());
        }
    }
    
    /**
     * Called when the view distance changes.
     */
    public void setViewDistance(int distance) {
        this.viewDistance = distance;
        culler.markDirty();
        needsRebuild = true;
        Arsenic.getLogger().info("View distance set to: {}", distance);
    }
    
    /**
     * Rebuilds the culler's chunk list from the current map.
     */
    public void rebuildIfNeeded() {
        if (!needsRebuild && !culler.needsRebuild()) return;
        
        long startTime = System.nanoTime();
        
        List<CullingTask.ChunkBounds> chunks = new ArrayList<>(chunkBoundsMap.values());
        culler.rebuildChunkList(chunks);
        
        needsRebuild = false;
        lastRebuildTime = System.nanoTime() - startTime;
        
        if (Arsenic.getLogger().isDebugEnabled()) {
            Arsenic.getLogger().debug("Rebuilt chunk list: {} chunks in {}ns", 
                chunks.size(), lastRebuildTime);
        }
    }
    
    /**
     * Forces a rebuild of the chunk list.
     */
    public void forceRebuild() {
        needsRebuild = true;
        rebuildIfNeeded();
    }
    
    /**
     * Gets the frustum culler.
     */
    public FrustumCuller getCuller() {
        return culler;
    }
    
    /**
     * Gets the current chunk count.
     */
    public int getChunkCount() {
        return chunkBoundsMap.size();
    }
    
    /**
     * Gets the total number of chunks loaded since initialization.
     */
    public int getTotalChunksLoaded() {
        return totalChunksLoaded;
    }
    
    /**
     * Gets the total number of chunks unloaded since initialization.
     */
    public int getTotalChunksUnloaded() {
        return totalChunksUnloaded;
    }
    
    /**
     * Gets the view distance.
     */
    public int getViewDistance() {
        return viewDistance;
    }
    
    /**
     * Gets the time of the last rebuild in nanoseconds.
     */
    public long getLastRebuildTime() {
        return lastRebuildTime;
    }
    
    /**
     * Checks if the chunk list needs rebuilding.
     */
    public boolean needsRebuild() {
        return needsRebuild || culler.needsRebuild();
    }
    
    /**
     * Checks if a chunk is currently loaded.
     */
    public boolean isChunkLoaded(int x, int z) {
        return chunkBoundsMap.containsKey(getChunkKey(x, z));
    }
    
    /**
     * Gets a chunk's bounds if loaded.
     */
    public CullingTask.ChunkBounds getChunkBounds(int x, int z) {
        return chunkBoundsMap.get(getChunkKey(x, z));
    }
    
    /**
     * Gets all loaded chunk bounds.
     */
    public Collection<CullingTask.ChunkBounds> getAllChunks() {
        return new ArrayList<>(chunkBoundsMap.values());
    }
    
    /**
     * Gets the list of chunk indices that are currently visible.
     */
    public Set<Integer> getVisibleChunks() {
        return culler.getVisibleChunks();
    }
    
    /**
     * Checks if a specific chunk is visible.
     */
    public boolean isChunkVisible(int index) {
        return culler.isChunkVisible(index);
    }
    
    /**
     * Resets all statistics.
     */
    public void resetStats() {
        totalChunksLoaded = 0;
        totalChunksUnloaded = 0;
        chunkLoadTime.clear();
        culler.resetMetrics();
    }
    
    /**
     * Gets a unique chunk index.
     */
    private static long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    /**
     * Creates chunk bounds from a Minecraft chunk.
     */
    private static CullingTask.ChunkBounds createBoundsFromChunk(Chunk chunk) {
        int x = chunk.getPos().x;
        int z = chunk.getPos().z;
        int index = x * 1000000 + z;
        
        float minX = x * 16;
        float minZ = z * 16;
        float maxX = minX + 16;
        float maxZ = minZ + 16;
        float minY = -64;
        float maxY = 320;
        
        return new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
    }
    
    /**
     * Gets a diagnostic string.
     */
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Arsenic ChunkManager Diagnostics ===\n");
        sb.append("Initialized: ").append(isInitialized).append("\n");
        sb.append("Loaded chunks: ").append(chunkBoundsMap.size()).append("\n");
        sb.append("Total loaded: ").append(totalChunksLoaded).append("\n");
        sb.append("Total unloaded: ").append(totalChunksUnloaded).append("\n");
        sb.append("View distance: ").append(viewDistance).append("\n");
        sb.append("Needs rebuild: ").append(needsRebuild).append("\n");
        sb.append("Last rebuild: ").append(lastRebuildTime).append("ns\n");
        sb.append("\n");
        sb.append(culler.getDiagnostics());
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("ChunkManager{chunks=%d, loaded=%d, unloaded=%d}",
            chunkBoundsMap.size(), totalChunksLoaded, totalChunksUnloaded);
    }
}