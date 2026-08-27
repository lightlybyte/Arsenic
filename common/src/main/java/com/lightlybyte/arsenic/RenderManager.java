package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.culling.FrustumCuller;
import com.lightlybyte.arsenic.threading.ThreadManager;
import org.joml.Matrix4f;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    
    public void initialize() {
        if (isInitialized) return;
        
        Arsenic.getLogger().info("Initializing RenderManager...");
        ChunkManager.getInstance().initialize();
        
        ThreadManager.getInstance().scheduleAtFixedRate(
            this::logStats,
            5, 5, java.util.concurrent.TimeUnit.SECONDS
        );
        
        isInitialized = true;
        Arsenic.getLogger().info("RenderManager initialized!");
    }
    
    public void preRender(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        if (!useArsenicRendering || !isInitialized) return;
        
        frameCount++;
        long startTime = System.nanoTime();
        
        try {
            FrustumCuller culler = ChunkManager.getInstance().getCuller();
            culler.updateFrustum(projectionMatrix, viewMatrix);
            
            // Use sync culling for immediate results
            Set<Integer> visible = culler.cullSync();
            visibleChunks.clear();
            visibleChunks.addAll(visible);
            renderedChunkCount = visible.size();
            skippedChunkCount = culler.getTotalChunkCount() - renderedChunkCount;
            
        } catch (Exception e) {
            Arsenic.getLogger().debug("Render pre-processing failed: " + e.getMessage());
        }
        
        lastRenderTime = System.nanoTime() - startTime;
    }
    
    public void postRender() {
        renderedThisFrame.clear();
    }
    
    public void beginFrame() {
        if (!useArsenicRendering || !isInitialized) return;
        renderedThisFrame.clear();
    }
    
    public void endFrame() {
        // Nothing needed
    }
    
    public boolean isChunkVisible(int index) {
        return visibleChunks.contains(index);
    }
    
    public Set<Integer> getVisibleChunks() {
        return new java.util.HashSet<>(visibleChunks);
    }
    
    public int getRenderedChunkCount() {
        return renderedChunkCount;
    }
    
    public int getSkippedChunkCount() {
        return skippedChunkCount;
    }
    
    public long getLastRenderTime() {
        return lastRenderTime;
    }
    
    public void setArsenicRenderingEnabled(boolean enabled) {
        this.useArsenicRendering = enabled;
        if (enabled) {
            Arsenic.getLogger().info("Arsenic rendering enabled");
        } else {
            Arsenic.getLogger().info("Arsenic rendering disabled - falling back to vanilla");
        }
    }
    
    public boolean isArsenicRenderingEnabled() {
        return useArsenicRendering;
    }
    
    public long getFrameCount() {
        return frameCount;
    }
    
    public void forceUpdate() {
        if (!isInitialized) return;
        FrustumCuller culler = ChunkManager.getInstance().getCuller();
        culler.forceUpdate();
    }
    
    private void logStats() {
        if (!isInitialized) return;
        
        FrustumCuller culler = ChunkManager.getInstance().getCuller();
        int totalChunks = culler.getTotalChunkCount();
        int visible = culler.getLastCulledCount();
        float visibilityRatio = totalChunks > 0 ? (float) visible / totalChunks * 100 : 0;
        long cullTime = culler.getLastCullingTimeMs();
        double avgCullTime = culler.getAverageCullingTimeMs();
        long renderTimeNs = lastRenderTime;
        
        // Use direct System.out for cleaner logging
        System.out.println("[Arsenic] === Arsenic Render Stats ===");
        System.out.println("[Arsenic]   Total Chunks: " + totalChunks);
        System.out.println("[Arsenic]   Visible Chunks: " + visible + " (" + String.format("%.1f", visibilityRatio) + "%)");
        System.out.println("[Arsenic]   Cull Time: " + cullTime + "ms (avg: " + String.format("%.2f", avgCullTime) + "ms)");
        System.out.println("[Arsenic]   Render Time: " + renderTimeNs + "ns");
        System.out.println("[Arsenic]   Frame: " + frameCount);
        System.out.println("[Arsenic]   Rendering: " + (useArsenicRendering ? "ENABLED" : "DISABLED"));
        
        if (totalChunks > 0) {
            double efficiency = culler.getCullingEfficiency() * 100;
            System.out.println("[Arsenic]   Culling Efficiency: " + String.format("%.1f", efficiency) + "%");
        }
    }
    
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