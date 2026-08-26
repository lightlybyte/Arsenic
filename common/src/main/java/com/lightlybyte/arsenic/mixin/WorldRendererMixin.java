package com.lightlybyte.arsenic.mixin;

import com.lightlybyte.arsenic.RenderManager;
import com.lightlybyte.arsenic.Arsenic;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public class WorldRendererMixin {
    
    private static boolean warned = false;
    
    @Inject(
        method = "render",
        at = @At("HEAD")
    )
    private void onRenderStart(MatrixStack matrices, float tickDelta, long limitTime, 
                               boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                               LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix,
                               CallbackInfo ci) {
        try {
            // Get the projection and view matrices from the render context
            Matrix4f viewMatrix = matrices.peek().getPositionMatrix();
            
            // Update the frustum in RenderManager
            RenderManager.getInstance().preRender(projectionMatrix, viewMatrix);
        } catch (Exception e) {
            // Silently fail - don't crash the game
            if (!warned) {
                Arsenic.getLogger().warn("Arsenic render hook failed: " + e.getMessage());
                warned = true;
            }
        }
    }
    
    @Inject(
        method = "render",
        at = @At("RETURN")
    )
    private void onRenderEnd(MatrixStack matrices, float tickDelta, long limitTime, 
                             boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                             LightmapTextureManager lightmapTextureManager, Matrix4f projectionMatrix,
                             CallbackInfo ci) {
        try {
            RenderManager.getInstance().postRender();
        } catch (Exception e) {
            // Silently fail
        }
    }
}