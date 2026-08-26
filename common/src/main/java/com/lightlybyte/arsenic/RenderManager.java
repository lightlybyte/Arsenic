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
    private final Set<Integer> renderedThisFrame = ConcurrentHashMap.newKeySet();
    
    private boolean isInitialized = false;
    private boolean useArsenicRendering = true;
    private long lastRenderTime = 0;
    private int renderedChunkCount = 0;
    private int skippedChunkCount = 0;
    private long frameCount = 0;
    private long lastStatsTime = 0;
    
    private RenderManager() {}
    
    public static RenderManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * Initializes the render manager.
     */
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
     * Updates the frustum and performs culling.
     */
    public void preRender(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        if (!useArsenicRendering || !isInitialized) return;
        
        frameCount++;
        long startTime = System.nanoTime();
        
        try {
            // Get the culler from chunk manager
            FrustumCuller culler = ChunkManager.getInstance().getCuller();
            
            // Update frustum from camera matrices
            culler.updateFrustum(projectionMatrix, viewMatrix);
            
            // Perform async culling
            culler.cullAsync().thenAccept(visible -> {
                visibleChunks.clear();
                visibleChunks.addAll(visible);
                renderedChunkCount = visible.size();
            });
            
            // Track skipped chunks for stats
            skippedChunkCount = culler.getTotalChunkCount() - renderedChunkCount;
            
        } catch (Exception e) {
            Arsenic.getLogger().debug("Render pre-processing failed: " + e.getMessage());
        }
        
        lastRenderTime = System.nanoTime() - startTime;
    }
    
    /**
     * Called after rendering to clean up.
     */
    public void postRender() {
        // Nothing needed yet
        renderedThisFrame.clear();
    }
    
    /**
     * Called every frame to prepare for rendering.
     */
    public void beginFrame() {
        if (!useArsenicRendering || !isInitialized) return;
        renderedThisFrame.clear();
    }
    
    /**
     * Called every frame to finish rendering.
     */
    public void endFrame() {
        // Nothing needed
    }
    
    /**
     * Checks if a chunk is visible.
     * Used by the renderer to decide whether to render a chunk.
     */
    public boolean isChunkVisible(int index) {
        return visibleChunks.contains(index);
    }
    
    /**
     * Gets the current set of visible chunk indices.
     */
    public Set<Integer> getVisibleChunks() {
        return new java.util.HashSet<>(visibleChunks);
    }
    
    /**
     * Gets the number of chunks rendered this frame.
     */
    public int getRenderedChunkCount() {
        return renderedChunkCount;
    }
    
    /**
     * Gets the number of chunks skipped this frame.
     */
    public int getSkippedChunkCount() {
        return skippedChunkCount;
    }
    
    /**
     * Gets the last render time in nanoseconds.
     */
    public long getLastRenderTime() {
        return lastRenderTime;
    }
    
    /**
     * Enables or disables Arsenic rendering.
     */
    public void setArsenicRenderingEnabled(boolean enabled) {
        this.useArsenicRendering = enabled;
        if (enabled) {
            Arsenic.getLogger().info("Arsenic rendering enabled");
        } else {
            Arsenic.getLogger().info("Arsenic rendering disabled - falling back to vanilla");
        }
    }
    
    /**
     * Checks if Arsenic rendering is enabled.
     */
    public boolean isArsenicRenderingEnabled() {
        return useArsenicRendering;
    }
    
    /**
     * Gets the frame count.
     */
    public long getFrameCount() {
        return frameCount;
    }
    
    /**
     * Logs performance statistics.
     */
    private void logStats() {
        if (!isInitialized) return;
        
        FrustumCuller culler = ChunkManager.getInstance().getCuller();
        
        // Get visibility stats
        int totalChunks = culler.getTotalChunkCount();
        int visible = culler.getLastCulledCount();
        float visibilityRatio = totalChunks > 0 ? (float) visible / totalChunks * 100 : 0;
        
        // Get performance stats
        long cullTime = culler.getLastCullingTimeMs();
        double avgCullTime = culler.getAverageCullingTimeMs();
        long renderTimeNs = lastRenderTime;
        
        // Log summary
        Arsenic.getLogger().info("=== Arsenic Render Stats ===");
        Arsenic.getLogger().info("  Total Chunks: {}", totalChunks);
        Arsenic.getLogger().info("  Visible Chunks: {} ({:.1f}%)", visible, visibilityRatio);
        Arsenic.getLogger().info("  Cull Time: {}ms (avg: {:.2f}ms)", cullTime, avgCullTime);
        Arsenic.getLogger().info("  Render Time: {}ns", renderTimeNs);
        Arsenic.getLogger().info("  Frame: {}", frameCount);
        Arsenic.getLogger().info("  Rendering: {}", useArsenicRendering ? "ENABLED" : "DISABLED");
        
        // Log frustum stats from culler
        if (culler.getTotalChunkCount() > 0) {
            double efficiency = culler.getCullingEfficiency() * 100;
            Arsenic.getLogger().info("  Culling Efficiency: {:.1f}%", efficiency);
        }
    }
    
    /**
     * Prints a detailed diagnostics report.
     */
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Arsenic RenderManager Diagnostics ===\n");
        sb.append("Initialized: ").append(isInitialized).append("\n");
        sb.append("Rendering enabled: ").append(useArsenicRendering).append("\n");
        sb.append("Frame count: ").append(frameCount).append("\n");
        sb.append("Visible chunks: ").append(renderedChunkCount).append("\n");
        sb.append("Skipped chunks: ").append(skippedChunkCount).append("\n");
        sb.append("Last render time: ").append(lastRenderTime).append("ns\n");
        sb.append("\n");
        sb.append(ChunkManager.getInstance().getDiagnostics());
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("RenderManager{chunks=%d, visible=%d, frame=%d}",
            ChunkManager.getInstance().getChunkCount(),
            renderedChunkCount,
            frameCount
        );
    }
}