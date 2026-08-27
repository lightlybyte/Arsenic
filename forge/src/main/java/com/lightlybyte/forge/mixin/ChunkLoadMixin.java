package com.lightlybyte.forge.mixin;

import com.lightlybyte.arsenic.ChunkManager;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelChunk.class)
public class ChunkLoadMixin {
    @Inject(method = "setLoaded", at = @At("TAIL"))
    private void onChunkLoaded(boolean loaded, CallbackInfo ci) {
        try {
            if (!loaded) return;
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
}