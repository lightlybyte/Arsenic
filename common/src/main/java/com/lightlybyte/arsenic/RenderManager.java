package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.culling.FrustumCuller;
import com.lightlybyte.arsenic.culling.CullingTask;
import com.lightlybyte.arsenic.threading.ThreadManager;
import org.joml.Matrix4f;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Arsenic's custom rendering pipeline.
 * Hooks into the game's render loop and replaces vanilla rendering.
 */
public class RenderManager {
    private static final RenderManager INSTANCE = new RenderManager();
    
    private final FrustumCuller culler = new FrustumCuller();
    private final Set<Integer> visibleChunks = ConcurrentHashMap.newKeySet();
    
    private boolean isInitialized = false;
    private boolean useArsenicRendering = true;
    private long lastRenderTime = 0;
    private int renderedChunkCount = 0;
    
    // Cache for chunk bounds to avoid recomputing every frame
    private final Map<Integer, CullingTask.ChunkBounds> chunkBoundsCache = new ConcurrentHashMap<>();
    
    private RenderManager() {}
    
    public static RenderManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Called when the game starts.
     * Initializes the render manager.
     */
    public void initialize() {
        if (isInitialized) return;
        
        System.out.println("[Arsenic] Initializing RenderManager...");
        
        // Start the render loop monitoring
        ThreadManager.getInstance().scheduleAtFixedRate(
            this::logRenderStats,
            5, 5, java.util.concurrent.TimeUnit.SECONDS
        );
        
        isInitialized = true;
        System.out.println("[Arsenic] RenderManager initialized!");
    }
    
    /**
     * Called every frame before rendering.
     * Updates the frustum and performs culling.
     */
    public void preRender(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        if (!useArsenicRendering) return;
        
        long startTime = System.nanoTime();
        
        // Update frustum
        culler.updateFrustum(projectionMatrix, viewMatrix);
        
        // If chunk list changed, rebuild
        if (culler.needsRebuild()) {
            rebuildChunkList();
        }
        
        // Perform async culling
        culler.cullAsync().thenAccept(visible -> {
            visibleChunks.clear();
            visibleChunks.addAll(visible);
            renderedChunkCount = visible.size();
        });
        
        lastRenderTime = System.nanoTime() - startTime;
    }
    
    /**
     * Called after rendering to clean up.
     */
    public void postRender() {
        // Nothing needed yet
    }
    
    /**
     * Rebuilds the chunk list from the current world.
     * This should be called when chunks load/unload.
     */
    public void rebuildChunkList() {
        // This will be called from the mixin when chunks change
        // The actual chunk data will be provided by the game
        System.out.println("[Arsenic] Rebuilding chunk list...");
        culler.rebuildChunkList(new ArrayList<>(chunkBoundsCache.values()));
    }
    
    /**
     * Adds a chunk to the culler.
     * Called from the mixin when a chunk is loaded.
     */
    public void addChunk(int chunkX, int chunkZ, int index, float minY, float maxY) {
        CullingTask.ChunkBounds bounds = FrustumCuller.createChunkBounds(
            chunkX, chunkZ, index, minY, maxY
        );
        chunkBoundsCache.put(index, bounds);
        culler.markDirty();
    }
    
    /**
     * Removes a chunk from the culler.
     * Called from the mixin when a chunk is unloaded.
     */
    public void removeChunk(int index) {
        chunkBoundsCache.remove(index);
        culler.markDirty();
    }
    
    /**
     * Checks if a chunk is visible.
     * Used by the renderer to decide whether to render a chunk.
     */
    public boolean isChunkVisible(int chunkIndex) {
        return visibleChunks.contains(chunkIndex);
    }
    
    /**
     * Gets the current set of visible chunk indices.
     */
    public Set<Integer> getVisibleChunks() {
        return new HashSet<>(visibleChunks);
    }
    
    // ==================== STATS & DIAGNOSTICS ====================
    
    private void logRenderStats() {
        System.out.println("[Arsenic] Render Stats:");
        System.out.println("  - Total chunks: " + culler.getTotalChunkCount());
        System.out.println("  - Visible chunks: " + culler.getLastCulledCount());
        System.out.println("  - Cull time: " + culler.getLastCullingTimeMs() + "ms");
        System.out.println("  - Render time: " + (lastRenderTime / 1_000_000) + "ms");
    }
    
    public FrustumCuller getCuller() {
        return culler;
    }
    
    public boolean isArsenicRenderingEnabled() {
        return useArsenicRendering;
    }
    
    public void setArsenicRenderingEnabled(boolean enabled) {
        this.useArsenicRendering = enabled;
        System.out.println("[Arsenic] Rendering " + (enabled ? "enabled" : "disabled"));
    }
    
    public int getRenderedChunkCount() {
        return renderedChunkCount;
    }
    
    public long getLastRenderTime() {
        return lastRenderTime;
    }
}