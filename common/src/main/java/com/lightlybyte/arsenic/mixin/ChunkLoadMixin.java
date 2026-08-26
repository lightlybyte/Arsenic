package com.lightlybyte.arsenic.mixin;

import com.lightlybyte.arsenic.ChunkManager;
import com.lightlybyte.arsenic.Arsenic;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public class ChunkLoadMixin {
    
    private static boolean warned = false;
    
    @Inject(
        method = "load",
        at = @At("RETURN")
    )
    private void onChunkLoaded(CallbackInfo ci) {
        try {
            WorldChunk chunk = (WorldChunk) (Object) this;
            ChunkManager.getInstance().onChunkLoaded(chunk);
        } catch (Exception e) {
            if (!warned) {
                Arsenic.getLogger().warn("Arsenic chunk load hook failed: " + e.getMessage());
                warned = true;
            }
        }
    }
    
    @Inject(
        method = "unload",
        at = @At("HEAD")
    )
    private void onChunkUnloaded(CallbackInfo ci) {
        try {
            WorldChunk chunk = (WorldChunk) (Object) this;
            ChunkManager.getInstance().onChunkUnloaded(chunk);
        } catch (Exception e) {
            // Silently fail
        }
    }
}