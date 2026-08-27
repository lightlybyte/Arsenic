package com.lightlybyte.forge.mixin;

import com.lightlybyte.arsenic.RenderManager;
import com.lightlybyte.arsenic.culling.FrustumCuller;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(ChunkRenderDispatcher.class)
public class ChunkRenderMixin {
    
    @Shadow private Level level;
    
    private final Map<BlockPos, Float> visibilityCache = new ConcurrentHashMap<>();
    private final Map<BlockPos, Long> lastCheckCache = new ConcurrentHashMap<>();
    private final Map<BlockPos, Integer> skipCounter = new ConcurrentHashMap<>();
    private final Map<BlockPos, Integer> renderCounter = new ConcurrentHashMap<>();
    private final Map<BlockPos, Float> visibilityScore = new ConcurrentHashMap<>();
    
    private static final int CACHE_TTL_MS = 3000;
    private static final int MIN_SKIP_TO_LOD = 5;
    private static final int MAX_VISIBILITY_SCORE = 100;
    
    private final AtomicLong totalSkipped = new AtomicLong(0);
    private final AtomicLong totalRendered = new AtomicLong(0);
    private final AtomicLong totalCacheHits = new AtomicLong(0);
    private final AtomicLong totalCacheMisses = new AtomicLong(0);
    
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void onScheduleChunk(BlockPos pos, CallbackInfo ci) {
        try {
            long startTime = System.nanoTime();
            
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int index = chunkX * 1000000 + chunkZ;
            
            // Quick visibility score check
            float score = visibilityScore.getOrDefault(pos, 0.5f);
            if (score < 0.1f) {
                // This chunk is very rarely visible, skip it
                skipCounter.put(pos, skipCounter.getOrDefault(pos, 0) + 1);
                totalSkipped.incrementAndGet();
                ci.cancel();
                return;
            }
            
            // Check cache
            boolean cachedVisible = checkCache(pos);
            if (cachedVisible) {
                renderCounter.put(pos, renderCounter.getOrDefault(pos, 0) + 1);
                totalRendered.incrementAndGet();
                return;
            }
            
            // Check render manager
            RenderManager renderManager = RenderManager.getInstance();
            boolean visible = renderManager.isChunkVisible(index);
            
            // Update cache and scores
            updateCache(pos, visible);
            updateVisibilityScore(pos, visible);
            
            if (!visible) {
                skipCounter.put(pos, skipCounter.getOrDefault(pos, 0) + 1);
                totalSkipped.incrementAndGet();
                
                // Check if we should reduce LOD for this chunk
                if (skipCounter.getOrDefault(pos, 0) > MIN_SKIP_TO_LOD) {
                    applyLODReduction(pos);
                }
                
                ci.cancel();
                
                long duration = System.nanoTime() - startTime;
                if (duration > 1_000_000) {
                    // Slow skip
                }
            } else {
                renderCounter.put(pos, renderCounter.getOrDefault(pos, 0) + 1);
                totalRendered.incrementAndGet();
            }
            
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void onCompileChunk(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int index = chunkX * 1000000 + chunkZ;
            
            RenderManager renderManager = RenderManager.getInstance();
            boolean visible = renderManager.isChunkVisible(index);
            
            if (!visible) {
                cir.setReturnValue(false);
                totalSkipped.incrementAndGet();
                updateVisibilityScore(pos, false);
            }
            
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    @Inject(method = "renderChunk", at = @At("HEAD"), cancellable = true)
    private void onRenderChunk(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int index = chunkX * 1000000 + chunkZ;
            
            RenderManager renderManager = RenderManager.getInstance();
            boolean visible = renderManager.isChunkVisible(index);
            
            if (!visible) {
                cir.setReturnValue(false);
                totalSkipped.incrementAndGet();
            }
            
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    private boolean checkCache(BlockPos pos) {
        long now = System.currentTimeMillis();
        Long lastCheck = lastCheckCache.get(pos);
        Float cached = visibilityCache.get(pos);
        
        if (lastCheck != null && cached != null) {
            if (now - lastCheck < CACHE_TTL_MS) {
                totalCacheHits.incrementAndGet();
                return cached > 0.5f;
            }
        }
        
        totalCacheMisses.incrementAndGet();
        return false;
    }
    
    private void updateCache(BlockPos pos, boolean visible) {
        visibilityCache.put(pos, visible ? 1.0f : 0.0f);
        lastCheckCache.put(pos, System.currentTimeMillis());
    }
    
    private void updateVisibilityScore(BlockPos pos, boolean visible) {
        float current = visibilityScore.getOrDefault(pos, 0.5f);
        if (visible) {
            current = Math.min(1.0f, current + 0.1f);
        } else {
            current = Math.max(0.0f, current - 0.05f);
        }
        visibilityScore.put(pos, current);
    }
    
    private void applyLODReduction(BlockPos pos) {
        // This chunk is consistently invisible, reduce its rendering priority
        // Could trigger LOD reduction or mark for culling
        skipCounter.put(pos, 0);
        
        // Notify chunk manager to reduce LOD for this area
        if (level != null) {
            LevelChunk chunk = level.getChunk(pos);
            if (chunk != null) {
                // Mark chunk for LOD reduction
            }
        }
    }
    
    public long getTotalSkipped() {
        return totalSkipped.get();
    }
    
    public long getTotalRendered() {
        return totalRendered.get();
    }
    
    public float getSkipPercentage() {
        long total = totalSkipped.get() + totalRendered.get();
        return total == 0 ? 0 : (float) totalSkipped.get() / total * 100;
    }
    
    public float getCacheHitPercentage() {
        long total = totalCacheHits.get() + totalCacheMisses.get();
        return total == 0 ? 0 : (float) totalCacheHits.get() / total * 100;
    }
    
    public void clearCache() {
        visibilityCache.clear();
        lastCheckCache.clear();
        skipCounter.clear();
        renderCounter.clear();
        visibilityScore.clear();
    }
    
    public String getStats() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Arsenic Chunk Render Stats ===\n");
        sb.append("Total skipped: ").append(totalSkipped.get()).append("\n");
        sb.append("Total rendered: ").append(totalRendered.get()).append("\n");
        sb.append("Skip rate: ").append(String.format("%.1f%%", getSkipPercentage())).append("\n");
        sb.append("Cache hit rate: ").append(String.format("%.1f%%", getCacheHitPercentage())).append("\n");
        sb.append("Cache size: ").append(visibilityCache.size()).append("\n");
        return sb.toString();
    }
}