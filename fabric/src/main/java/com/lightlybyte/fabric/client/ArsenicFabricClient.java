package com.lightlybyte.fabric.client;

import com.lightlybyte.arsenic.RenderManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

/**
 * Fabric client-only entry point.
 * This is separate from the main entry point to allow client-specific initialization.
 */
@Environment(EnvType.CLIENT)
public class ArsenicFabricClient implements ClientModInitializer {
    
    @Override
    public void onInitializeClient() {
        // Client-specific initialization
        // RenderManager is already initialized via Arsenic.init() in the main entry
        
        // Ensure RenderManager is accessible
        RenderManager.getInstance();
        
        // Log client initialization
        com.lightlybyte.arsenic.Arsenic.getLogger().info("Fabric client-specific initialization complete!");
    }
}