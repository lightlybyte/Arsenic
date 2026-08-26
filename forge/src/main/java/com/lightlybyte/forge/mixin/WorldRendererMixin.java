package com.lightlybyte.forge.mixin;

import com.lightlybyte.arsenic.RenderManager;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class WorldRendererMixin {
    @Inject(method = "renderLevel", at = @At("HEAD"))
    private void onRenderStart(PoseStack poseStack, float partialTick, long finishTimeNano,
                               boolean renderBlockOutline, Camera camera, GameRenderer gameRenderer,
                               LightTexture lightTexture, Matrix4f projectionMatrix,
                               CallbackInfo ci) {
        try {
            Matrix4f viewMatrix = poseStack.last().pose();
            RenderManager.getInstance().preRender(projectionMatrix, viewMatrix);
        } catch (Exception e) {
            // Silently fail
        }
    }
}