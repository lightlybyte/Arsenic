package com.lightlybyte.forge.mixin;

import com.lightlybyte.arsenic.RenderManager;
import net.minecraft.client.renderer.chunk.ChunkRenderDispatcher;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRenderDispatcher.class)
public class ChunkRendererMixin {
    @Inject(method = "compile", at = @At("HEAD"), cancellable = true)
    private void onCompileChunk(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        try {
            int chunkX = pos.getX() >> 4;
            int chunkZ = pos.getZ() >> 4;
            int index = chunkX * 1000000 + chunkZ;
            if (!RenderManager.getInstance().isChunkVisible(index)) {
                cir.setReturnValue(false);
            }
        } catch (Exception e) {
            // Silently fail
        }
    }
}