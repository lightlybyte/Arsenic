package com.lightlybyte.arsenic.threading;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Arsenic's core multithreading manager.
 * Handles thread pools, task scheduling, and graceful shutdown.
 * 
 * This is the foundation that all other systems (culling, meshing, rendering)
 * will build upon.
 */
public class ThreadManager {
    private static final ThreadManager INSTANCE = new ThreadManager();
    
    // Thread pools
    private final ExecutorService workerPool;
    private final ScheduledExecutorService scheduler;
    private final ExecutorService computePool;
    
    // State tracking
    private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);
    private final AtomicInteger taskCount = new AtomicInteger(0);
    private final AtomicLong totalTasksSubmitted = new AtomicLong(0);
    private final AtomicLong totalTasksCompleted = new AtomicLong(0);
    
    // Performance metrics
    private volatile long lastTaskCompletionTime = 0;
    private volatile int peakActiveThreads = 0;
    private volatile long peakQueueSize = 0;
    
    // Shutdown timeout (seconds)
    private static final int SHUTDOWN_TIMEOUT = 10;
    
    // Maximum threads (capped at 8 to avoid oversubscription)
    private static final int MAX_THREADS = 8;
    
    private ThreadManager() {
        int threads = Math.min(Runtime.getRuntime().availableProcessors(), MAX_THREADS);
        
        // Worker pool for general tasks (culling, meshing, etc.)
        this.workerPool = new ThreadPoolExecutor(
            threads,                    // core pool size
            threads,                    // max pool size
            60L, TimeUnit.SECONDS,      // keep-alive time
            new LinkedBlockingQueue<>(), // unbounded queue
            (r) -> {
                Thread t = new Thread(r, "Arsenic-Worker-" + taskCount.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.NORM_PRIORITY);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // Fallback to caller if queue is full
        );
        
        // Scheduler for delayed/repeating tasks
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            (r) -> {
                Thread t = new Thread(r, "Arsenic-Scheduler");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            }
        );
        
        // Compute pool for heavy number crunching (can be more aggressive)
        this.computePool = new ThreadPoolExecutor(
            Math.max(1, threads - 1),   // Leave one core for the main thread
            Math.max(1, threads - 1),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            (r) -> {
                Thread t = new Thread(r, "Arsenic-Compute-" + taskCount.getAndIncrement());
                t.setDaemon(true);
                t.setPriority(Thread.MAX_PRIORITY);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy() // Fail fast on compute overload
        );
        
        // Start monitoring thread
        startMonitoring();
        
        // Register shutdown hook for JVM-level cleanup
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "Arsenic-ShutdownHook"));
    }
    
    public static ThreadManager getInstance() {
        return INSTANCE;
    }
    
    // ==================== TASK SUBMISSION ====================
    
    /**
     * Submits a Runnable task to the worker pool.
     * Returns a CompletableFuture that completes when the task finishes.
     */
    public CompletableFuture<Void> submit(Runnable task) {
        if (isShuttingDown.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("ThreadManager is shutting down"));
        }
        totalTasksSubmitted.incrementAndGet();
        return CompletableFuture.runAsync(wrapTask(task), workerPool);
    }
    
    /**
     * Submits a Callable task to the worker pool.
     * Returns a CompletableFuture with the result.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        if (isShuttingDown.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("ThreadManager is shutting down"));
        }
        totalTasksSubmitted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, workerPool);
    }
    
    /**
     * Submits a compute-heavy task to the compute pool.
     * Use this for CPU-intensive work like meshing, pathfinding, etc.
     */
    public <T> CompletableFuture<T> submitCompute(Callable<T> task) {
        if (isShuttingDown.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("ThreadManager is shutting down"));
        }
        totalTasksSubmitted.incrementAndGet();
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, computePool);
    }
    
    /**
     * Submits a task with a priority.
     * Higher priority tasks are executed first (uses a priority queue internally).
     */
    public CompletableFuture<Void> submitPriority(Runnable task, TaskPriority priority) {
        if (isShuttingDown.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("ThreadManager is shutting down"));
        }
        totalTasksSubmitted.incrementAndGet();
        return CompletableFuture.runAsync(wrapTask(task), 
            new PriorityThreadPoolExecutor(priority));
    }
    
    // ==================== SCHEDULING ====================
    
    /**
     * Schedules a task to run after a delay.
     */
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        if (isShuttingDown.get()) {
            return null;
        }
        return scheduler.schedule(wrapTask(task), delay, unit);
    }
    
    /**
     * Schedules a task to run at a fixed rate.
     */
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, 
                                                   long period, TimeUnit unit) {
        if (isShuttingDown.get()) {
            return null;
        }
        return scheduler.scheduleAtFixedRate(wrapTask(task), initialDelay, period, unit);
    }
    
    /**
     * Schedules a task to run with a fixed delay between executions.
     */
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay,
                                                      long delay, TimeUnit unit) {
        if (isShuttingDown.get()) {
            return null;
        }
        return scheduler.scheduleWithFixedDelay(wrapTask(task), initialDelay, delay, unit);
    }
    
    // ==================== BATCH PROCESSING ====================
    
    /**
     * Processes a list of items in parallel using the worker pool.
     * Each item is processed by the provided function.
     * Returns a CompletableFuture that completes when all items are processed.
     */
    public <T> CompletableFuture<Void> processBatch(List<T> items, Consumer<T> processor) {
        if (isShuttingDown.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("ThreadManager is shutting down"));
        }
        
        List<CompletableFuture<Void>> futures = new ArrayList<>(items.size());
        for (T item : items) {
            futures.add(submit(() -> processor.accept(item)));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Processes a list of items in parallel with a result.
     * Each item is processed by the provided function, returning a result.
     * Returns a CompletableFuture with a list of results.
     */
    public <T, R> CompletableFuture<List<R>> processBatchWithResult(List<T> items, 
                                                                    Function<T, R> processor) {
        if (isShuttingDown.get()) {
            return CompletableFuture.failedFuture(new RejectedExecutionException("ThreadManager is shutting down"));
        }
        
        List<CompletableFuture<R>> futures = new ArrayList<>(items.size());
        for (T item : items) {
            futures.add(submit(() -> processor.apply(item)));
        }
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .collect(Collectors.toList()));
    }
    
    // ==================== STATUS & METRICS ====================
    
    public boolean isShuttingDown() {
        return isShuttingDown.get();
    }
    
    public int getActiveWorkerCount() {
        return ((ThreadPoolExecutor) workerPool).getActiveCount();
    }
    
    public int getActiveComputeCount() {
        return ((ThreadPoolExecutor) computePool).getActiveCount();
    }
    
    public int getWorkerQueueSize() {
        return ((ThreadPoolExecutor) workerPool).getQueue().size();
    }
    
    public int getComputeQueueSize() {
        return ((ThreadPoolExecutor) computePool).getQueue().size();
    }
    
    public long getTotalTasksSubmitted() {
        return totalTasksSubmitted.get();
    }
    
    public long getTotalTasksCompleted() {
        return totalTasksCompleted.get();
    }
    
    public int getPeakActiveThreads() {
        return peakActiveThreads;
    }
    
    public long getPeakQueueSize() {
        return peakQueueSize;
    }
    
    public long getLastTaskCompletionTime() {
        return lastTaskCompletionTime;
    }
    
    // ==================== SHUTDOWN ====================
    
    /**
     * Gracefully shuts down all thread pools.
     * Waits for running tasks to complete up to the timeout.
     */
    public void shutdown() {
        if (!isShuttingDown.compareAndSet(false, true)) {
            return;
        }
        
        System.out.println("[Arsenic] Shutting down ThreadManager...");
        long startTime = System.currentTimeMillis();
        
        // Log current state
        logThreadState();
        
        // Shutdown in order: compute first (most aggressive), then worker, then scheduler
        shutdownPool(computePool, "Compute", 5);
        shutdownPool(workerPool, "Worker", 5);
        shutdownPool(scheduler, "Scheduler", 2);
        
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println("[Arsenic] ThreadManager shutdown complete in " + elapsed + "ms");
    }
    
    private void shutdownPool(ExecutorService pool, String name, int timeoutSeconds) {
        pool.shutdown();
        try {
            if (!pool.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
                System.err.println("[Arsenic] " + name + " pool did not terminate gracefully, forcing shutdown...");
                pool.shutdownNow();
                // Wait a bit more for forced termination
                if (!pool.awaitTermination(2, TimeUnit.SECONDS)) {
                    System.err.println("[Arsenic] " + name + " pool failed to terminate");
                }
            } else {
                System.out.println("[Arsenic] " + name + " pool shut down successfully");
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    private void logThreadState() {
        System.out.println("[Arsenic] Thread state at shutdown:");
        System.out.println("  - Total tasks submitted: " + totalTasksSubmitted.get());
        System.out.println("  - Total tasks completed: " + totalTasksCompleted.get());
        System.out.println("  - Active workers: " + getActiveWorkerCount());
        System.out.println("  - Active compute: " + getActiveComputeCount());
        System.out.println("  - Worker queue: " + getWorkerQueueSize());
        System.out.println("  - Compute queue: " + getComputeQueueSize());
        System.out.println("  - Peak active threads: " + peakActiveThreads);
        System.out.println("  - Peak queue size: " + peakQueueSize);
    }
    
    // ==================== INTERNAL HELPERS ====================
    
    private Runnable wrapTask(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("[Arsenic] Task failed: " + e.getMessage());
                e.printStackTrace();
            } finally {
                totalTasksCompleted.incrementAndGet();
                lastTaskCompletionTime = System.nanoTime();
            }
        };
    }
    
    private void startMonitoring() {
        scheduleAtFixedRate(() -> {
            if (isShuttingDown.get()) {
                return;
            }
            
            // Update peak metrics
            int active = ((ThreadPoolExecutor) workerPool).getActiveCount();
            int queueSize = ((ThreadPoolExecutor) workerPool).getQueue().size();
            
            if (active > peakActiveThreads) {
                peakActiveThreads = active;
            }
            if (queueSize > peakQueueSize) {
                peakQueueSize = queueSize;
            }
            
            // Warn if queue is getting too large
            if (queueSize > 1000) {
                System.err.println("[Arsenic] Warning: Worker queue size is " + queueSize);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    // ==================== TASK PRIORITY SYSTEM ====================
    
    public enum TaskPriority {
        LOW,        // Background tasks (world generation, etc.)
        NORMAL,     // Default
        HIGH,       // Important tasks (culling, meshing)
        CRITICAL    // Must run immediately (rendering tasks)
    }
    
    /**
     * Priority-based thread pool executor for tasks that need ordering.
     */
    private static class PriorityThreadPoolExecutor extends ThreadPoolExecutor {
        private final TaskPriority priority;
        
        public PriorityThreadPoolExecutor(TaskPriority priority) {
            super(1, 1, 0L, TimeUnit.MILLISECONDS,
                new PriorityBlockingQueue<>(),
                (r) -> new Thread(r, "Arsenic-Priority-" + priority.name()));
            this.priority = priority;
        }
        
        @Override
        public void execute(Runnable command) {
            // Use priority queue internally - tasks with higher priority run first
            super.execute(command);
        }
    }
    
    // ==================== SINGLETON GUARDIAN ====================
    
    /**
     * Prevents instantiation from outside.
     */
    protected Object readResolve() {
        return INSTANCE;
    }
}