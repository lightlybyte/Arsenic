package com.lightlybyte.fabric;

import com.lightlybyte.arsenic.Arsenic;
import com.lightlybyte.arsenic.threading.ThreadManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public class ArsenicFabric implements ModInitializer, ClientModInitializer {
    
    private static boolean isClientInitialized = false;
    private static boolean isServerInitialized = false;
    private static long startTime = 0;
    
    @Override
    public void onInitialize() {
        if (isServerInitialized) return;
        startTime = System.currentTimeMillis();
        
        Arsenic.printBanner();
        Arsenic.init();
        
        registerServerEvents();
        
        isServerInitialized = true;
        Arsenic.getLogger().info("Fabric server-side initialization complete! ({}ms)", 
            System.currentTimeMillis() - startTime);
    }
    
    @Override
    public void onInitializeClient() {
        if (isClientInitialized) return;
        
        Arsenic.getLogger().info("Fabric client-side initialization...");
        
        // Client-specific initialization
        // RenderManager is already initialized via Arsenic.init()
        
        isClientInitialized = true;
        Arsenic.getLogger().info("Fabric client-side initialization complete!");
    }
    
    private void registerServerEvents() {
        ServerLifecycleEvents.SERVER_STOPPING.register((server) -> {
            Arsenic.getLogger().info("Server stopping, shutting down Arsenic...");
            ThreadManager.getInstance().shutdown();
        });
        
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            if (server.getTickCount() % 100 == 0 && Arsenic.isInitialized()) {
                ThreadManager tm = ThreadManager.getInstance();
                // Uncomment for debug stats
                // Arsenic.getLogger().debug("Stats - Active: {}, Queue: {}, Tasks: {}", 
                //     tm.getActiveWorkerCount(), tm.getWorkerQueueSize(), 
                //     tm.getTotalTasksSubmitted());
            }
        });
    }
    
    public static boolean isClientReady() {
        return isClientInitialized;
    }
    
    public static boolean isServerReady() {
        return isServerInitialized;
    }
}