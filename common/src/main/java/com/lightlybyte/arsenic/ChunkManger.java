package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.culling.CullingTask;
import com.lightlybyte.arsenic.culling.FrustumCuller;
import com.lightlybyte.arsenic.threading.ThreadManager;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the chunk list for frustum culling.
 * Tracks loaded chunks and updates the culler when chunks change.
 */
public class ChunkManager {
    private static final ChunkManager INSTANCE = new ChunkManager();
    
    private final FrustumCuller culler = new FrustumCuller();
    private final Map<Long, CullingTask.ChunkBounds> chunkBoundsMap = new ConcurrentHashMap<>();
    
    private volatile boolean isInitialized = false;
    private int viewDistance = 8;
    
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
    }
    
    /**
     * Called when a chunk is loaded.
     */
    public void onChunkLoaded(Chunk chunk) {
        if (!isInitialized) return;
        
        long key = getChunkKey(chunk.getPos().x, chunk.getPos().z);
        CullingTask.ChunkBounds bounds = createBoundsFromChunk(chunk);
        
        chunkBoundsMap.put(key, bounds);
        culler.markDirty();
        
        if (Arsenic.getLogger().isDebugEnabled()) {
            Arsenic.getLogger().debug("Chunk loaded: " + chunk.getPos().x + ", " + chunk.getPos().z);
        }
    }
    
    /**
     * Called when a chunk is unloaded.
     */
    public void onChunkUnloaded(Chunk chunk) {
        if (!isInitialized) return;
        
        long key = getChunkKey(chunk.getPos().x, chunk.getPos().z);
        chunkBoundsMap.remove(key);
        culler.markDirty();
        
        if (Arsenic.getLogger().isDebugEnabled()) {
            Arsenic.getLogger().debug("Chunk unloaded: " + chunk.getPos().x + ", " + chunk.getPos().z);
        }
    }
    
    /**
     * Called when view distance changes.
     */
    public void setViewDistance(int distance) {
        this.viewDistance = distance;
        culler.markDirty();
        Arsenic.getLogger().info("View distance set to: " + distance);
    }
    
    /**
     * Rebuilds the culler's chunk list from the current map.
     */
    public void rebuildIfNeeded() {
        if (!culler.needsRebuild()) return;
        
        List<CullingTask.ChunkBounds> chunks = new ArrayList<>(chunkBoundsMap.values());
        culler.rebuildChunkList(chunks);
        
        if (Arsenic.getLogger().isDebugEnabled()) {
            Arsenic.getLogger().debug("Rebuilt chunk list: " + chunks.size() + " chunks");
        }
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
     * Gets the view distance.
     */
    public int getViewDistance() {
        return viewDistance;
    }
    
    /**
     * Checks if a chunk position is within view distance.
     */
    public boolean isWithinViewDistance(int chunkX, int chunkZ, int playerChunkX, int playerChunkZ) {
        int dx = chunkX - playerChunkX;
        int dz = chunkZ - playerChunkZ;
        return dx * dx + dz * dz <= viewDistance * viewDistance;
    }
    
    // ==================== HELPERS ====================
    
    private static long getChunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
    
    private static CullingTask.ChunkBounds createBoundsFromChunk(Chunk chunk) {
        int x = chunk.getPos().x;
        int z = chunk.getPos().z;
        int index = x * 1000000 + z; // Simple unique index
        
        float minX = x * 16;
        float minZ = z * 16;
        float maxX = minX + 16;
        float maxZ = minZ + 16;
        
        // Get actual chunk height from the chunk
        float minY = -64;
        float maxY = 320;
        
        // If we have heightmap data, use it
        try {
            var heightmap = chunk.getHeightmap(net.minecraft.world.Heightmap.Type.WORLD_SURFACE);
            if (heightmap != null) {
                // We can get more accurate bounds, but for now use full height
            }
        } catch (Exception e) {
            // Fallback to full height
        }
        
        return new CullingTask.ChunkBounds(index, minX, minY, minZ, maxX, maxY, maxZ);
    }
}