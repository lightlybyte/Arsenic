package com.lightlybyte.arsenic.mixin;

import com.lightlybyte.arsenic.ChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    
    /**
     * Called when chunks are loaded on the client.
     */
    @Inject(
        method = "loadChunk",
        at = @At("RETURN")
    )
    private void onLoadChunk(int x, int z, WorldChunk chunk, CallbackInfo ci) {
        ChunkManager.getInstance().onChunkLoaded(chunk);
    }
    
    /**
     * Called when chunks are unloaded on the client.
     */
    @Inject(
        method = "unloadChunk",
        at = @At("HEAD")
    )
    private void onUnloadChunk(int x, int z, CallbackInfo ci) {
        // The chunk is being unloaded, but we don't have the chunk object here
        // We'll handle this differently - mark dirty instead
        ChunkManager.getInstance().getCuller().markDirty();
    }
}