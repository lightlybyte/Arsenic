package com.lightlybyte.forge;

import com.lightlybyte.arsenic.Arsenic;
import com.lightlybyte.arsenic.threading.ThreadManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod("arsenic")
public class ArsenicForge {
    
    private static boolean isClientInitialized = false;
    private static boolean isServerInitialized = false;
    private static long startTime = 0;
    
    public ArsenicForge() {
        startTime = System.currentTimeMillis();
        
        Arsenic.printBanner();
        
        // Register setup events
        var modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::onCommonSetup);
        modEventBus.addListener(this::onClientSetup);
        
        // Register Forge event bus for server lifecycle events
        MinecraftForge.EVENT_BUS.register(this);
        
        Arsenic.getLogger().info("Forge mod instance created!");
    }
    
    private void onCommonSetup(FMLCommonSetupEvent event) {
        if (isServerInitialized) return;
        
        event.enqueueWork(() -> {
            Arsenic.init();
            isServerInitialized = true;
            Arsenic.getLogger().info("Forge common setup complete! ({}ms)", 
                System.currentTimeMillis() - startTime);
        });
    }
    
    private void onClientSetup(FMLClientSetupEvent event) {
        if (isClientInitialized) return;
        
        event.enqueueWork(() -> {
            // Client-specific initialization
            // RenderManager is already initialized via Arsenic.init()
            isClientInitialized = true;
            Arsenic.getLogger().info("Forge client setup complete!");
        });
    }
    
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        Arsenic.getLogger().info("Server stopped, shutting down Arsenic...");
        ThreadManager.getInstance().shutdown();
    }
    
    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            if (event.getServer().getTickCount() % 100 == 0 && Arsenic.isInitialized()) {
                ThreadManager tm = ThreadManager.getInstance();
                // Uncomment for debug stats
                // Arsenic.getLogger().debug("Stats - Active: {}, Queue: {}, Tasks: {}", 
                //     tm.getActiveWorkerCount(), tm.getWorkerQueueSize(), 
                //     tm.getTotalTasksSubmitted());
            }
        }
    }
    
    public static boolean isClientReady() {
        return isClientInitialized;
    }
    
    public static boolean isServerReady() {
        return isServerInitialized;
    }
}