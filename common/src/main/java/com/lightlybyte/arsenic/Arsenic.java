package com.lightlybyte.arsenic;

import com.lightlybyte.arsenic.threading.ThreadManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Arsenic - The true greater version of Sodium.
 * 
 * Main entry point for the Arsenic mod.
 * Initializes all systems: threading, rendering, culling, and meshing.
 */
public class Arsenic {
    public static final String MOD_ID = "arsenic";
    public static final String MOD_NAME = "Arsenic";
    public static final String VERSION = "0.1.0";
    
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static boolean initialized = false;
    
    /**
     * Called when the mod is loaded on any platform (Fabric/Forge).
     * This is the single initialization point for all Arsenic systems.
     */
    public static void init() {
        if (initialized) {
            LOGGER.warn("Arsenic already initialized, skipping duplicate init");
            return;
        }
        
        LOGGER.info("=== Arsenic v{} ===", VERSION);
        LOGGER.info("Loading Arsenic - The true greater version of Sodium");
        
        // Initialize the thread manager first (core dependency)
        ThreadManager threadManager = ThreadManager.getInstance();
        LOGGER.info("ThreadManager initialized with {} workers", 
            Runtime.getRuntime().availableProcessors());
        
        // Initialize the render manager
        RenderManager renderManager = RenderManager.getInstance();
        renderManager.initialize();
        LOGGER.info("RenderManager initialized");
        
        // Log system info
        logSystemInfo();
        
        initialized = true;
        LOGGER.info("Arsenic initialization complete!");
    }
    
    /**
     * Called when the mod is being shut down.
     * Cleanly shuts down all Arsenic systems.
     */
    public static void shutdown() {
        if (!initialized) {
            return;
        }
        
        LOGGER.info("Shutting down Arsenic...");
        
        // Shutdown thread manager (this will shutdown all dependent systems)
        ThreadManager.getInstance().shutdown();
        
        initialized = false;
        LOGGER.info("Arsenic shutdown complete");
    }
    
    /**
     * Checks if Arsenic is initialized.
     */
    public static boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Gets the mod ID.
     */
    public static String getModId() {
        return MOD_ID;
    }
    
    /**
     * Gets the mod version.
     */
    public static String getVersion() {
        return VERSION;
    }
    
    /**
     * Gets the logger for Arsenic.
     */
    public static Logger getLogger() {
        return LOGGER;
    }
    
    private static void logSystemInfo() {
        LOGGER.info("System Information:");
        LOGGER.info("  - OS: {} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        LOGGER.info("  - Architecture: {}", System.getProperty("os.arch"));
        LOGGER.info("  - Java Version: {}", System.getProperty("java.version"));
        LOGGER.info("  - Available Processors: {}", Runtime.getRuntime().availableProcessors());
        LOGGER.info("  - Max Memory: {} MB", Runtime.getRuntime().maxMemory() / 1024 / 1024);
        
        // Log Arsenic features
        LOGGER.info("Arsenic Features:");
        LOGGER.info("  - Multithreaded Frustum Culling: ENABLED");
        LOGGER.info("  - Greedy Meshing: PENDING");
        LOGGER.info("  - Batch Rendering: PENDING");
        LOGGER.info("  - OpenGL 4.6 Support: PENDING");
        LOGGER.info("  - Vulkan Support: PENDING");
        LOGGER.info("  - Shader Support: PENDING");
    }
    
    /**
     * Prints a banner to the log.
     */
    public static void printBanner() {
        LOGGER.info("ARSENIC!");
    }
}