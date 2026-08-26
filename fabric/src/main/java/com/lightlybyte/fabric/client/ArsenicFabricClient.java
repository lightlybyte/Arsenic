package com.lightlybyte.fabric.client;

import com.lightlybyte.arsenic.Arsenic;
import com.lightlybyte.arsenic.RenderManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.Environment;
import net.fabricmc.api.EnvType;

@Environment(EnvType.CLIENT)
public class ArsenicFabricClient implements ClientModInitializer {
    
    private static boolean initialized = false;
    
    @Override
    public void onInitializeClient() {
        if (initialized) return;
        
        Arsenic.getLogger().info("Fabric client-specific initialization...");
        
        // Ensure RenderManager is initialized
        RenderManager.getInstance();
        
        initialized = true;
        Arsenic.getLogger().info("Fabric client-specific initialization complete!");
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
}