package com.lightlybyte.fabric.mixin;

import com.lightlybyte.arsenic.ChunkManager;
import net.minecraft.class_2818;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_2818.class)
public class ChunkLoadMixin {
    @Inject(method = "method_12089", at = @At("RETURN"))
    private void onChunkLoaded(CallbackInfo ci) {
        try {
            class_2818 chunk = (class_2818) (Object) this;
            int x = chunk.method_12156().field_5552;
            int z = chunk.method_12156().field_5553;
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
    
    @Inject(method = "method_12125", at = @At("HEAD"))
    private void onChunkUnloaded(CallbackInfo ci) {
        try {
            class_2818 chunk = (class_2818) (Object) this;
            int x = chunk.method_12156().field_5552;
            int z = chunk.method_12156().field_5553;
            int index = x * 1000000 + z;
            ChunkManager.getInstance().removeChunkBounds(index);
        } catch (Exception e) {
            // Silently fail
        }
    }
}