package com.lightlybyte.fabric;

import com.lightlybyte.arsenic.Arsenic;
import com.lightlybyte.arsenic.threading.ThreadManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.ServerStopping;

/**
 * Fabric entry point for Arsenic.
 * Implements both ModInitializer and ClientModInitializer for full compatibility.
 */
public class ArsenicFabric implements ModInitializer, ClientModInitializer {
    
    private static boolean isClientInitialized = false;
    private static boolean isServerInitialized = false;
    
    /**
     * Called when the mod is loaded on a dedicated server or integrated server.
     */
    @Override
    public void onInitialize() {
        if (isServerInitialized) {
            return;
        }
        
        Arsenic.printBanner();
        Arsenic.init();
        
        // Register server lifecycle events
        registerServerEvents();
        
        isServerInitialized = true;
        Arsenic.getLogger().info("Fabric server-side initialization complete!");
    }
    
    /**
     * Called when the mod is loaded on a client.
     * This runs after onInitialize().
     */
    @Override
    public void onInitializeClient() {
        if (isClientInitialized) {
            return;
        }
        
        // Client-specific initialization
        // (RenderManager is already initialized via Arsenic.init())
        
        isClientInitialized = true;
        Arsenic.getLogger().info("Fabric client-side initialization complete!");
    }
    
    /**
     * Registers Fabric lifecycle event handlers.
     */
    private void registerServerEvents() {
        // Shutdown hook - clean up when server stops
        ServerStopping.EVENT.register((server) -> {
            Arsenic.getLogger().info("Server stopping, shutting down Arsenic...");
            ThreadManager.getInstance().shutdown();
        });
        
        // Optional: Log performance stats periodically
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            // Log stats every 100 ticks (5 seconds)
            if (server.getTickCount() % 100 == 0) {
                ThreadManager tm = ThreadManager.getInstance();
                if (Arsenic.isInitialized()) {
                    // Uncomment for debugging
                    // Arsenic.getLogger().debug("Stats - Active: {}, Queue: {}, Tasks: {}", 
                    //     tm.getActiveWorkerCount(), tm.getWorkerQueueSize(), 
                    //     tm.getTotalTasksSubmitted());
                }
            }
        });
    }
    
    /**
     * Gets the mod instance for Fabric.
     */
    public static boolean isClientReady() {
        return isClientInitialized;
    }
    
    public static boolean isServerReady() {
        return isServerInitialized;
    }
}