package com.lightlybyte.fabric.mixin;

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

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mixin(ChunkRenderDispatcher.class)
public class ChunkRenderMixin {
    
    @Shadow private Level level;
    
    private final Map<BlockPos, Long> lastRenderAttempt = new ConcurrentHashMap<>();
    private final Map<BlockPos, Integer> skipCount = new ConcurrentHashMap<>();
    private final Map<BlockPos, Integer> renderCount = new ConcurrentHashMap<>();
    private final Map<BlockPos, Float> visibilityCache = new ConcurrentHashMap<>();
    
    private static final int CACHE_TIMEOUT_MS = 5000;
    private static final int MAX_SKIP_COUNT = 3;
    private static final float VISIBILITY_THRESHOLD = 0.1f;
    
    private final AtomicLong totalSkipped = new AtomicLong(0);
    private final AtomicLong totalRendered = new AtomicLong(0);
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true)
    private void onScheduleChunk(BlockPos pos, CallbackInfo ci) {
        try {
            long startTime = System.nanoTime();
            
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int index = chunkX * 1000000 + chunkZ;
            
            // Check visibility cache
            boolean isVisible = checkVisibilityCache(pos);
            
            if (isVisible) {
                // Early exit - chunk is visible, render it
                renderCount.put(pos, renderCount.getOrDefault(pos, 0) + 1);
                totalRendered.incrementAndGet();
                return;
            }
            
            // Check render manager for visibility
            RenderManager renderManager = RenderManager.getInstance();
            boolean visible = renderManager.isChunkVisible(index);
            
            // Update cache
            updateVisibilityCache(pos, visible);
            
            if (!visible) {
                // Skip rendering this chunk
                skipCount.put(pos, skipCount.getOrDefault(pos, 0) + 1);
                totalSkipped.incrementAndGet();
                
                // Check if this chunk is consistently skipped
                int skipCount = this.skipCount.getOrDefault(pos, 0);
                if (skipCount > MAX_SKIP_COUNT) {
                    // This chunk is consistently invisible, mark for LOD reduction
                    processInvisibleChunk(pos);
                }
                
                ci.cancel();
                
                // Track performance
                long endTime = System.nanoTime();
                long duration = endTime - startTime;
                
                // Log if skipping takes too long
                if (duration > 1000000) { // 1ms
                    // System.out.println("[Arsenic] Slow chunk skip: " + duration + "ns for chunk " + chunkX + "," + chunkZ);
                }
            } else {
                renderCount.put(pos, renderCount.getOrDefault(pos, 0) + 1);
                totalRendered.incrementAndGet();
            }
            
        } catch (Exception e) {
            // If anything fails, let vanilla handle it
            // System.err.println("[Arsenic] Chunk render mixin failed: " + e.getMessage());
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
                // Skip compiling this chunk
                cir.setReturnValue(false);
                totalSkipped.incrementAndGet();
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
                // Skip rendering this chunk
                cir.setReturnValue(false);
                totalSkipped.incrementAndGet();
            }
            
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    private boolean checkVisibilityCache(BlockPos pos) {
        long now = System.currentTimeMillis();
        Float cached = visibilityCache.get(pos);
        Long lastCheck = lastRenderAttempt.get(pos);
        
        if (cached != null && lastCheck != null) {
            if (now - lastCheck < CACHE_TIMEOUT_MS) {
                cacheHits.incrementAndGet();
                return cached;
            }
        }
        
        cacheMisses.incrementAndGet();
        return false;
    }
    
    private void updateVisibilityCache(BlockPos pos, boolean visible) {
        visibilityCache.put(pos, visible ? 1.0f : 0.0f);
        lastRenderAttempt.put(pos, System.currentTimeMillis());
    }
    
    private void processInvisibleChunk(BlockPos pos) {
        // This chunk is consistently invisible, we can reduce its priority
        // or mark it for LOD reduction
        
        // Check if there's a chunk at this position
        if (level != null) {
            LevelChunk chunk = level.getChunk(pos);
            if (chunk != null) {
                // Mark as low priority for rendering
                // This could trigger LOD reduction in a future system
                skipCount.put(pos, 0); // Reset counter to avoid constant logging
            }
        }
    }
    
    public long getSkippedChunks() {
        return totalSkipped.get();
    }
    
    public long getRenderedChunks() {
        return totalRendered.get();
    }
    
    public float getSkipRate() {
        long total = totalSkipped.get() + totalRendered.get();
        return total == 0 ? 0 : (float) totalSkipped.get() / total;
    }
    
    public long getCacheHitRate() {
        long total = cacheHits.get() + cacheMisses.get();
        return total == 0 ? 0 : cacheHits.get() * 100 / total;
    }
}