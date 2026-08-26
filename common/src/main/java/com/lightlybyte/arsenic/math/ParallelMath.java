package com.lightlybyte.arsenic.math;

import com.lightlybyte.arsenic.threading.ThreadManager;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * Parallel math operations optimized for chunk processing.
 * Hundreds of lines of parallel processing utilities.
 */
public class ParallelMath {
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    private static final int CHUNK_SIZE = Math.max(16, 1024 / THREADS);
    private static final ForkJoinPool POOL = new ForkJoinPool(THREADS);
    
    public static <T> void forEachParallel(List<T> items, Consumer<T> action) {
        if (items.size() < CHUNK_SIZE) {
            for (T item : items) action.accept(item);
            return;
        }
        ThreadManager.getInstance().processBatch(items, action);
    }
    
    public static <T> void forEachParallel(T[] items, Consumer<T> action) {
        if (items.length < CHUNK_SIZE) {
            for (T item : items) action.accept(item);
            return;
        }
        List<T> list = Arrays.asList(items);
        ThreadManager.getInstance().processBatch(list, action);
    }
    
    public static <T, R> List<R> mapParallel(List<T> items, Function<T, R> mapper) {
        if (items.size() < CHUNK_SIZE) {
            List<R> results = new ArrayList<>(items.size());
            for (T item : items) results.add(mapper.apply(item));
            return results;
        }
        return ThreadManager.getInstance().processBatchWithResult(items, mapper).join();
    }
    
    public static <T, R> R[] mapParallel(T[] items, Function<T, R> mapper, IntFunction<R[]> arrayFactory) {
        List<R> results = mapParallel(Arrays.asList(items), mapper);
        return results.toArray(arrayFactory.apply(results.size()));
    }
    
    public static float[] parallelFloatArray(int size, IntToDoubleFunction generator) {
        float[] array = new float[size];
        if (size < CHUNK_SIZE * 4) {
            for (int i = 0; i < size; i++) array[i] = (float) generator.applyAsDouble(i);
            return array;
        }
        int chunkSize = Math.max(64, size / THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int start = 0; start < size; start += chunkSize) {
            int end = Math.min(start + chunkSize, size);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < end; i++) {
                    array[i] = (float) generator.applyAsDouble(i);
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return array;
    }
    
    public static int[] parallelIntArray(int size, IntUnaryOperator generator) {
        int[] array = new int[size];
        if (size < CHUNK_SIZE * 4) {
            for (int i = 0; i < size; i++) array[i] = generator.applyAsInt(i);
            return array;
        }
        int chunkSize = Math.max(64, size / THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int start = 0; start < size; start += chunkSize) {
            int end = Math.min(start + chunkSize, size);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < end; i++) {
                    array[i] = generator.applyAsInt(i);
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return array;
    }
    
    public static double[] parallelDoubleArray(int size, IntToDoubleFunction generator) {
        double[] array = new double[size];
        if (size < CHUNK_SIZE * 4) {
            for (int i = 0; i < size; i++) array[i] = generator.applyAsDouble(i);
            return array;
        }
        int chunkSize = Math.max(64, size / THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int start = 0; start < size; start += chunkSize) {
            int end = Math.min(start + chunkSize, size);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < end; i++) {
                    array[i] = generator.applyAsDouble(i);
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return array;
    }
    
    public static <T> boolean anyParallel(List<T> items, Predicate<T> predicate) {
        if (items.size() < CHUNK_SIZE) {
            for (T item : items) if (predicate.test(item)) return true;
            return false;
        }
        int chunkSize = Math.max(64, items.size() / THREADS);
        List<CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (int start = 0; start < items.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, items.size());
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < end; i++) {
                    if (predicate.test(items.get(i))) return true;
                }
                return false;
            }));
        }
        for (CompletableFuture<Boolean> future : futures) {
            if (future.join()) return true;
        }
        return false;
    }
    
    public static <T> boolean allParallel(List<T> items, Predicate<T> predicate) {
        return !anyParallel(items, predicate.negate());
    }
    
    public static <T> int countParallel(List<T> items, Predicate<T> predicate) {
        if (items.size() < CHUNK_SIZE) {
            int count = 0;
            for (T item : items) if (predicate.test(item)) count++;
            return count;
        }
        int chunkSize = Math.max(64, items.size() / THREADS);
        List<CompletableFuture<Integer>> futures = new ArrayList<>();
        for (int start = 0; start < items.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, items.size());
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                int count = 0;
                for (int i = finalStart; i < end; i++) {
                    if (predicate.test(items.get(i))) count++;
                }
                return count;
            }));
        }
        int total = 0;
        for (CompletableFuture<Integer> future : futures) {
            total += future.join();
        }
        return total;
    }
    
    public static <T> Optional<T> findFirstParallel(List<T> items, Predicate<T> predicate) {
        if (items.size() < CHUNK_SIZE) {
            for (T item : items) if (predicate.test(item)) return Optional.of(item);
            return Optional.empty();
        }
        int chunkSize = Math.max(64, items.size() / THREADS);
        List<CompletableFuture<Optional<T>>> futures = new ArrayList<>();
        for (int start = 0; start < items.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, items.size());
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < end; i++) {
                    T item = items.get(i);
                    if (predicate.test(item)) return Optional.of(item);
                }
                return Optional.empty();
            }));
        }
        for (CompletableFuture<Optional<T>> future : futures) {
            Optional<T> result = future.join();
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }
    
    public static <T> T reduceParallel(List<T> items, T identity, BinaryOperator<T> accumulator) {
        if (items.size() < CHUNK_SIZE) {
            T result = identity;
            for (T item : items) result = accumulator.apply(result, item);
            return result;
        }
        int chunkSize = Math.max(64, items.size() / THREADS);
        List<CompletableFuture<T>> futures = new ArrayList<>();
        for (int start = 0; start < items.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, items.size());
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                T result = identity;
                for (int i = finalStart; i < end; i++) {
                    result = accumulator.apply(result, items.get(i));
                }
                return result;
            }));
        }
        T result = identity;
        for (CompletableFuture<T> future : futures) {
            result = accumulator.apply(result, future.join());
        }
        return result;
    }
    
    public static float sumParallel(float[] array) {
        if (array.length < CHUNK_SIZE) {
            float sum = 0;
            for (float v : array) sum += v;
            return sum;
        }
        int chunkSize = Math.max(64, array.length / THREADS);
        List<CompletableFuture<Float>> futures = new ArrayList<>();
        for (int start = 0; start < array.length; start += chunkSize) {
            int end = Math.min(start + chunkSize, array.length);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                float sum = 0;
                for (int i = finalStart; i < end; i++) sum += array[i];
                return sum;
            }));
        }
        float total = 0;
        for (CompletableFuture<Float> future : futures) {
            total += future.join();
        }
        return total;
    }
    
    public static double sumParallel(double[] array) {
        if (array.length < CHUNK_SIZE) {
            double sum = 0;
            for (double v : array) sum += v;
            return sum;
        }
        int chunkSize = Math.max(64, array.length / THREADS);
        List<CompletableFuture<Double>> futures = new ArrayList<>();
        for (int start = 0; start < array.length; start += chunkSize) {
            int end = Math.min(start + chunkSize, array.length);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                double sum = 0;
                for (int i = finalStart; i < end; i++) sum += array[i];
                return sum;
            }));
        }
        double total = 0;
        for (CompletableFuture<Double> future : futures) {
            total += future.join();
        }
        return total;
    }
    
    public static float minParallel(float[] array) {
        if (array.length < CHUNK_SIZE) {
            float min = Float.MAX_VALUE;
            for (float v : array) if (v < min) min = v;
            return min;
        }
        int chunkSize = Math.max(64, array.length / THREADS);
        List<CompletableFuture<Float>> futures = new ArrayList<>();
        for (int start = 0; start < array.length; start += chunkSize) {
            int end = Math.min(start + chunkSize, array.length);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                float min = Float.MAX_VALUE;
                for (int i = finalStart; i < end; i++) if (array[i] < min) min = array[i];
                return min;
            }));
        }
        float min = Float.MAX_VALUE;
        for (CompletableFuture<Float> future : futures) {
            float v = future.join();
            if (v < min) min = v;
        }
        return min;
    }
    
    public static float maxParallel(float[] array) {
        if (array.length < CHUNK_SIZE) {
            float max = Float.MIN_VALUE;
            for (float v : array) if (v > max) max = v;
            return max;
        }
        int chunkSize = Math.max(64, array.length / THREADS);
        List<CompletableFuture<Float>> futures = new ArrayList<>();
        for (int start = 0; start < array.length; start += chunkSize) {
            int end = Math.min(start + chunkSize, array.length);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                float max = Float.MIN_VALUE;
                for (int i = finalStart; i < end; i++) if (array[i] > max) max = array[i];
                return max;
            }));
        }
        float max = Float.MIN_VALUE;
        for (CompletableFuture<Float> future : futures) {
            float v = future.join();
            if (v > max) max = v;
        }
        return max;
    }
    
    public static <T> List<T> filterParallel(List<T> items, Predicate<T> predicate) {
        if (items.size() < CHUNK_SIZE) {
            List<T> results = new ArrayList<>();
            for (T item : items) if (predicate.test(item)) results.add(item);
            return results;
        }
        int chunkSize = Math.max(64, items.size() / THREADS);
        List<CompletableFuture<List<T>>> futures = new ArrayList<>();
        for (int start = 0; start < items.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, items.size());
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                List<T> results = new ArrayList<>();
                for (int i = finalStart; i < end; i++) {
                    T item = items.get(i);
                    if (predicate.test(item)) results.add(item);
                }
                return results;
            }));
        }
        List<T> results = new ArrayList<>();
        for (CompletableFuture<List<T>> future : futures) {
            results.addAll(future.join());
        }
        return results;
    }
    
    public static <T> List<T> sortParallel(List<T> items, Comparator<T> comparator) {
        if (items.size() < CHUNK_SIZE * 4) {
            List<T> copy = new ArrayList<>(items);
            copy.sort(comparator);
            return copy;
        }
        int chunkSize = Math.max(64, items.size() / THREADS);
        List<CompletableFuture<List<T>>> futures = new ArrayList<>();
        for (int start = 0; start < items.size(); start += chunkSize) {
            int end = Math.min(start + chunkSize, items.size());
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                List<T> chunk = new ArrayList<>(items.subList(finalStart, end));
                chunk.sort(comparator);
                return chunk;
            }));
        }
        List<T> results = new ArrayList<>();
        for (CompletableFuture<List<T>> future : futures) {
            results.addAll(future.join());
        }
        results.sort(comparator);
        return results;
    }
    
    public static float[] parallelAdd(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Arrays must be same length");
        float[] result = new float[a.length];
        if (a.length < CHUNK_SIZE) {
            for (int i = 0; i < a.length; i++) result[i] = a[i] + b[i];
            return result;
        }
        int chunkSize = Math.max(64, a.length / THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int start = 0; start < a.length; start += chunkSize) {
            int end = Math.min(start + chunkSize, a.length);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < end; i++) {
                    result[i] = a[i] + b[i];
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return result;
    }
    
    public static float[] parallelMul(float[] a, float[] b) {
        if (a.length != b.length) throw new IllegalArgumentException("Arrays must be same length");
        float[] result = new float[a.length];
        if (a.length < CHUNK_SIZE) {
            for (int i = 0; i < a.length; i++) result[i] = a[i] * b[i];
            return result;
        }
        int chunkSize = Math.max(64, a.length / THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int start = 0; start < a.length; start += chunkSize) {
            int end = Math.min(start + chunkSize, a.length);
            int finalStart = start;
            futures.add(ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < end; i++) {
                    result[i] = a[i] * b[i];
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return result;
    }
    
    public static int getThreadCount() {
        return THREADS;
    }
    
    public static int getChunkSize() {
        return CHUNK_SIZE;
    }
    
    public static ForkJoinPool getPool() {
        return POOL;
    }
}