package com.lightlybyte.arsenic.mixin;

import com.lightlybyte.arsenic.RenderManager;
import com.lightlybyte.arsenic.Arsenic;
import net.minecraft.client.render.chunk.ChunkRenderer;
import net.minecraft.client.render.chunk.ChunkRendererRegion;
import net.minecraft.client.render.chunk.ChunkRendererRegionBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRenderer.class)
public class ChunkRendererMixin {
    
    private static boolean warned = false;
    
    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private void onRenderChunk(ChunkRendererRegion region, BlockPos pos, 
                               CallbackInfoReturnable<Boolean> cir) {
        try {
            // Get the chunk index from the position
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int index = chunkX * 1000000 + chunkZ;
            
            // Check if this chunk is visible
            if (!RenderManager.getInstance().isChunkVisible(index)) {
                // Skip rendering this chunk
                cir.setReturnValue(false);
            }
        } catch (Exception e) {
            // If anything goes wrong, let vanilla handle it
            if (!warned) {
                Arsenic.getLogger().warn("Arsenic chunk render hook failed: " + e.getMessage());
                warned = true;
            }
        }
    }
}