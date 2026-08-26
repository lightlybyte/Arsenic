package com.lightlybyte.arsenic.mixin;

import com.lightlybyte.arsenic.ChunkManager;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldChunk.class)
public class ChunkMixin {
    
    /**
     * Called when a chunk is loaded.
     */
    @Inject(method = "load", at = @At("RETURN"))
    private void onChunkLoaded(CallbackInfo ci) {
        Chunk chunk = (Chunk) (Object) this;
        ChunkManager.getInstance().onChunkLoaded(chunk);
    }
    
    /**
     * Called when a chunk is unloaded.
     */
    @Inject(method = "unload", at = @At("HEAD"))
    private void onChunkUnloaded(CallbackInfo ci) {
        Chunk chunk = (Chunk) (Object) this;
        ChunkManager.getInstance().onChunkUnloaded(chunk);
    }
}