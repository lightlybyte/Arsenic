package com.lightlybyte.forge;

import com.lightlybyte.arsenic.Arsenic;
import com.lightlybyte.arsenic.threading.ThreadManager;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge entry point for Arsenic.
 * Uses the @Mod annotation to register with the Forge mod loader.
 */
@Mod("arsenic")
public class ArsenicForge {
    
    private static boolean isClientInitialized = false;
    private static boolean isServerInitialized = false;
    
    /**
     * Constructor is called when the mod is loaded by Forge.
     */
    public ArsenicForge() {
        Arsenic.printBanner();
        
        // Register setup events
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onCommonSetup);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        
        // Register Forge event bus for server lifecycle events
        MinecraftForge.EVENT_BUS.register(this);
        
        // Register config (optional - will add later)
        // ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        
        Arsenic.getLogger().info("Forge mod instance created!");
    }
    
    /**
     * Called during the common setup phase.
     * This runs on both client and server.
     */
    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (isServerInitialized) {
            return;
        }
        
        event.enqueueWork(() -> {
            Arsenic.init();
            isServerInitialized = true;
            Arsenic.getLogger().info("Forge common setup complete!");
        });
    }
    
    /**
     * Called during the client setup phase.
     * This runs only on the client.
     */
    private void onClientSetup(FMLClientSetupEvent event) {
        if (isClientInitialized) {
            return;
        }
        
        event.enqueueWork(() -> {
            // Client-specific initialization
            // (RenderManager is already initialized via Arsenic.init())
            isClientInitialized = true;
            Arsenic.getLogger().info("Forge client setup complete!");
        });
    }
    
    /**
     * Called when the server stops.
     * Cleans up Arsenic resources.
     */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Arsenic.getLogger().info("Server stopped, shutting down Arsenic...");
        ThreadManager.getInstance().shutdown();
    }
    
    /**
     * Optional: Log performance stats periodically.
     */
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            // Log stats every 100 ticks (5 seconds)
            if (event.getServer().getTickCount() % 100 == 0) {
                ThreadManager tm = ThreadManager.getInstance();
                if (Arsenic.isInitialized()) {
                    // Uncomment for debugging
                    // Arsenic.getLogger().debug("Stats - Active: {}, Queue: {}, Tasks: {}", 
                    //     tm.getActiveWorkerCount(), tm.getWorkerQueueSize(), 
                    //     tm.getTotalTasksSubmitted());
                }
            }
        }
    }
    
    /**
     * Gets the mod instance for Forge.
     */
    public static boolean isClientReady() {
        return isClientInitialized;
    }
    
    public static boolean isServerReady() {
        return isServerInitialized;
    }
}