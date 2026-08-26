package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.threading.ThreadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Arsenic {
    public static final String MOD_ID = "arsenic";
    public static final String MOD_NAME = "Arsenic";
    public static final String VERSION = "0.1.0";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean initialized = false;
    
    public static void init() {
        if (initialized) return;
        
        printBanner();
        LOGGER.info("Loading Arsenic - The true greater version of Sodium");
        
        ThreadManager threadManager = ThreadManager.getInstance();
        LOGGER.info("ThreadManager initialized with {} workers", 
            Runtime.getRuntime().availableProcessors());
        
        ChunkManager chunkManager = ChunkManager.getInstance();
        chunkManager.initialize();
        LOGGER.info("ChunkManager initialized");
        
        RenderManager renderManager = RenderManager.getInstance();
        renderManager.initialize();
        LOGGER.info("RenderManager initialized");
        
        logSystemInfo();
        initialized = true;
        LOGGER.info("Arsenic initialization complete!");
    }
    
    public static void shutdown() {
        if (!initialized) return;
        LOGGER.info("Shutting down Arsenic...");
        ThreadManager.getInstance().shutdown();
        initialized = false;
        LOGGER.info("Arsenic shutdown complete");
    }
    
    public static boolean isInitialized() {
        return initialized;
    }
    
    public static String getModId() {
        return MOD_ID;
    }
    
    public static String getVersion() {
        return VERSION;
    }
    
    public static Logger getLogger() {
        return LOGGER;
    }
    
    public static RenderManager getRenderManager() {
        return RenderManager.getInstance();
    }
    
    public static ChunkManager getChunkManager() {
        return ChunkManager.getInstance();
    }
    
    public static void enableRendering() {
        RenderManager.getInstance().setArsenicRenderingEnabled(true);
        LOGGER.info("Arsenic rendering enabled");
    }
    
    public static void disableRendering() {
        RenderManager.getInstance().setArsenicRenderingEnabled(false);
        LOGGER.info("Arsenic rendering disabled");
    }
    
    private static void logSystemInfo() {
        LOGGER.info("System Information:");
        LOGGER.info("  - OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        LOGGER.info("  - Architecture: {}", System.getProperty("os.arch"));
        LOGGER.info("  - Java Version: {}", System.getProperty("java.version"));
        LOGGER.info("  - Available Processors: {}", Runtime.getRuntime().availableProcessors());
        LOGGER.info("  - Max Memory: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        LOGGER.info("Arsenic Features:");
        LOGGER.info("  - Multithreaded Frustum Culling: ENABLED");
        LOGGER.info("  - Greedy Meshing: PENDING");
        LOGGER.info("  - Batch Rendering: PENDING");
        LOGGER.info("  - OpenGL 4.6 Support: PENDING");
        LOGGER.info("  - Vulkan Support: PENDING");
        LOGGER.info("  - Shader Support: PENDING");
        LOGGER.info("  - Multi-Loader: Fabric + Forge + NeoForge");
    }
    
    public static void printBanner() {
        LOGGER.info("Arsenic");
        LOGGER.info("   - better than sodium");
    }
}