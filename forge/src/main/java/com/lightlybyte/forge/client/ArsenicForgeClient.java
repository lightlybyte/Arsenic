package com.lightlybyte.forge.client;

import com.lightlybyte.arsenic.Arsenic;
import com.lightlybyte.arsenic.RenderManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod.EventBusSubscriber(modid = "arsenic", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ArsenicForgeClient {
    
    private static boolean initialized = false;
    
    public ArsenicForgeClient() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    private void onClientSetup(FMLClientSetupEvent event) {
        if (initialized) return;
        
        Arsenic.getLogger().info("Forge client-specific initialization...");
        
        // Ensure RenderManager is initialized
        RenderManager.getInstance();
        
        initialized = true;
        Arsenic.getLogger().info("Forge client-specific initialization complete!");
    }
    
    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            // Client-side render hook if needed
            // Currently handled by mixins
        }
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
}