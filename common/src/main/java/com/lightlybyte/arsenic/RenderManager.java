package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.culling.FrustumCuller;
import com.lightlybyte.arsenic.threading.ThreadManager;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Arsenic's custom rendering pipeline.
 * Hooks into the game's render loop and replaces vanilla rendering.
 */
public class RenderManager {
    private static final RenderManager INSTANCE = new RenderManager();
    
    private final Set<Integer> visibleChunks = ConcurrentHashMap.newKeySet();
    
    private boolean isInitialized = false;
    private boolean useArsenicRendering = true;
    private long lastRenderTime = 0;
    private int renderedChunkCount = 0;
    
    private RenderManager() {}
    
    public static RenderManager getInstance() {
        return INSTANCE;
    }
    
    public void initialize() {
        if (isInitialized) return;
        
        Arsenic.getLogger().info("Initializing RenderManager...");
        
        // Initialize chunk manager
        ChunkManager.getInstance().initialize();
        
        // Start stats logging
        ThreadManager.getInstance().scheduleAtFixedRate(
            this::logStats,
            5, 5, java.util.concurrent.TimeUnit.SECONDS
        );
        
        isInitialized = true;
        Arsenic.getLogger().info("RenderManager initialized!");
    }
    
    /**
     * Called every frame before rendering.
     */
    public void preRender(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        if (!useArsenicRendering || !isInitialized) return;
        
        long startTime = System.nanoTime();
        
        // Get the culler from chunk manager
        FrustumCuller culler = ChunkManager.getInstance().getCuller();
        
        // Update frustum
        culler.updateFrustum(projectionMatrix, viewMatrix);
        
        // Perform culling
        culler.cullAsync().thenAccept(visible -> {
            visibleChunks.clear();
            visibleChunks.addAll(visible);
            renderedChunkCount = visible.size();
        });
        
        lastRenderTime = System.nanoTime() - startTime;
    }
    
    /**
     * Called after rendering.
     */
    public void postRender() {
        // Nothing needed yet
    }
    
    /**
     * Checks if a chunk is visible.
     */
    public boolean isChunkVisible(int index) {
        return visibleChunks.contains(index);
    }
    
    /**
     * Gets visible chunks set.
     */
    public Set<Integer> getVisibleChunks() {
        return visibleChunks;
    }
    
    // ==================== STATS ====================
    
    private void logStats() {
        if (!isInitialized) return;
        
        FrustumCuller culler = ChunkManager.getInstance().getCuller();
        Arsenic.getLogger().info("Render Stats:");
        Arsenic.getLogger().info("  - Total chunks: " + culler.getTotalChunkCount());
        Arsenic.getLogger().info("  - Visible chunks: " + culler.getLastCulledCount());
        Arsenic.getLogger().info("  - Cull time: " + culler.getLastCullingTimeMs() + "ms");
        Arsenic.getLogger().info("  - Visibility ratio: " + 
            String.format("%.1f%%", culler.getVisibilityRatio() * 100));
    }
    
    public boolean isArsenicRenderingEnabled() {
        return useArsenicRendering;
    }
    
    public void setArsenicRenderingEnabled(boolean enabled) {
        this.useArsenicRendering = enabled;
        Arsenic.getLogger().info("Rendering " + (enabled ? "enabled" : "disabled"));
    }
    
    public int getRenderedChunkCount() {
        return renderedChunkCount;
    }
    
    public long getLastRenderTime() {
        return lastRenderTime;
    }
}