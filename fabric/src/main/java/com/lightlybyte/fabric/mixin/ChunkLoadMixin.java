package com.lightlybyte.fabric.mixin;

import com.lightlybyte.arsenic.ChunkManager;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class ChunkLoadMixin {
    @Inject(method = "load", at = @At("RETURN"))
    private void onChunkLoaded(CallbackInfo ci) {
        try {
            LevelChunk chunk = (LevelChunk) (Object) this;
            int x = chunk.getPos().x;
            int z = chunk.getPos().z;
            int index = x * 1000000 + z;
            float minX = x * 16;
            float minZ = z * 16;
            float maxX = minX + 16;
            float maxZ = minZ + 16;
            ChunkManager.getInstance().addChunkBounds(index, minX, -64, minZ, maxX, 320, maxZ);
        } catch (Exception e) {
            // Silently fail
        }
    }
    
    @Inject(method = "unload", at = @At("HEAD"))
    private void onChunkUnloaded(CallbackInfo ci) {
        try {
            LevelChunk chunk = (LevelChunk) (Object) this;
            int x = chunk.getPos().x;
            int z = chunk.getPos().z;
            int index = x * 1000000 + z;
            ChunkManager.getInstance().removeChunkBounds(index);
        } catch (Exception e) {
            // Silently fail
        }
    }
}