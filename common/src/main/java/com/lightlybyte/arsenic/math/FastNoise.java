package com.lightlybyte.arsenic.math;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

/**
 * Ultra-fast noise generation for terrain, culling, and procedural generation.
 * Hundreds of lines of optimized noise functions.
 */
public class FastNoise {
    private static final int[] PERM = new int[512];
    private static final float[] GRAD = new float[512];
    private static final int[] PERM_3D = new int[512];
    private static final float[] GRAD_3D = new float[768];
    
    private static final float F2 = 0.3660254037844386f;
    private static final float G2 = 0.21132486540518713f;
    private static final float F3 = 1.0f / 3.0f;
    private static final float G3 = 1.0f / 6.0f;
    private static final float F4 = (float) ((Math.sqrt(5.0f) - 1.0) / 4.0);
    private static final float G4 = (float) ((5.0 - Math.sqrt(5.0)) / 20.0);
    
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    
    static {
        for (int i = 0; i < 256; i++) {
            PERM[i] = i;
            PERM_3D[i] = i;
            GRAD[i] = (ThreadLocalRandom.current().nextFloat() * 2 - 1);
            GRAD_3D[i * 3] = (ThreadLocalRandom.current().nextFloat() * 2 - 1);
            GRAD_3D[i * 3 + 1] = (ThreadLocalRandom.current().nextFloat() * 2 - 1);
            GRAD_3D[i * 3 + 2] = (ThreadLocalRandom.current().nextFloat() * 2 - 1);
        }
        for (int i = 0; i < 256; i++) {
            int j = ThreadLocalRandom.current().nextInt(256);
            int tmp = PERM[i];
            PERM[i] = PERM[j];
            PERM[j] = tmp;
            tmp = PERM_3D[i];
            PERM_3D[i] = PERM_3D[j];
            PERM_3D[j] = tmp;
        }
        System.arraycopy(PERM, 0, PERM, 256, 256);
        System.arraycopy(PERM_3D, 0, PERM_3D, 256, 256);
        System.arraycopy(GRAD, 0, GRAD, 256, 256);
        System.arraycopy(GRAD_3D, 0, GRAD_3D, 768, 768);
    }
    
    public static float simplex2D(float x, float y) {
        float s = (x + y) * F2;
        int i = MathHelper.floorDiv((int) Math.floor(x + s), 1);
        int j = MathHelper.floorDiv((int) Math.floor(y + s), 1);
        float t = (i + j) * G2;
        float X0 = i - t;
        float Y0 = j - t;
        float x0 = x - X0;
        float y0 = y - Y0;
        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; } else { i1 = 0; j1 = 1; }
        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1 + 2 * G2;
        float y2 = y0 - 1 + 2 * G2;
        float n0 = 0, n1 = 0, n2 = 0;
        float t0 = 0.5f - x0 * x0 - y0 * y0;
        if (t0 >= 0) { t0 *= t0; n0 = t0 * t0 * grad2D(i, j, x0, y0); }
        float t1 = 0.5f - x1 * x1 - y1 * y1;
        if (t1 >= 0) { t1 *= t1; n1 = t1 * t1 * grad2D(i + i1, j + j1, x1, y1); }
        float t2 = 0.5f - x2 * x2 - y2 * y2;
        if (t2 >= 0) { t2 *= t2; n2 = t2 * t2 * grad2D(i + 1, j + 1, x2, y2); }
        return 70.0f * (n0 + n1 + n2);
    }
    
    public static float simplex3D(float x, float y, float z) {
        float s = (x + y + z) * F3;
        int i = MathHelper.floorDiv((int) Math.floor(x + s), 1);
        int j = MathHelper.floorDiv((int) Math.floor(y + s), 1);
        int k = MathHelper.floorDiv((int) Math.floor(z + s), 1);
        float t = (i + j + k) * G3;
        float X0 = i - t;
        float Y0 = j - t;
        float Z0 = k - t;
        float x0 = x - X0;
        float y0 = y - Y0;
        float z0 = z - Z0;
        int i1, j1, k1;
        int i2, j2, k2;
        if (x0 >= y0) {
            if (y0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
            else if (x0 >= z0) { i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1; }
            else { i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1; }
        } else {
            if (y0 < z0) { i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1; }
            else if (x0 < z0) { i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1; }
            else { i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0; }
        }
        float x1 = x0 - i1 + G3;
        float y1 = y0 - j1 + G3;
        float z1 = z0 - k1 + G3;
        float x2 = x0 - i2 + 2 * G3;
        float y2 = y0 - j2 + 2 * G3;
        float z2 = z0 - k2 + 2 * G3;
        float x3 = x0 - 1 + 3 * G3;
        float y3 = y0 - 1 + 3 * G3;
        float z3 = z0 - 1 + 3 * G3;
        float n0 = 0, n1 = 0, n2 = 0, n3 = 0;
        float t0 = 0.6f - x0 * x0 - y0 * y0 - z0 * z0;
        if (t0 >= 0) { t0 *= t0; n0 = t0 * t0 * grad3D(i, j, k, x0, y0, z0); }
        float t1 = 0.6f - x1 * x1 - y1 * y1 - z1 * z1;
        if (t1 >= 0) { t1 *= t1; n1 = t1 * t1 * grad3D(i + i1, j + j1, k + k1, x1, y1, z1); }
        float t2 = 0.6f - x2 * x2 - y2 * y2 - z2 * z2;
        if (t2 >= 0) { t2 *= t2; n2 = t2 * t2 * grad3D(i + i2, j + j2, k + k2, x2, y2, z2); }
        float t3 = 0.6f - x3 * x3 - y3 * y3 - z3 * z3;
        if (t3 >= 0) { t3 *= t3; n3 = t3 * t3 * grad3D(i + 1, j + 1, k + 1, x3, y3, z3); }
        return 32.0f * (n0 + n1 + n2 + n3);
    }
    
    public static float fractalNoise2D(float x, float y, int octaves, float lacunarity, float gain) {
        float value = 0;
        float amplitude = 1;
        float frequency = 1;
        float maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            value += amplitude * simplex2D(x * frequency, y * frequency);
            maxValue += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }
    
    public static float fractalNoise3D(float x, float y, float z, int octaves, float lacunarity, float gain) {
        float value = 0;
        float amplitude = 1;
        float frequency = 1;
        float maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            value += amplitude * simplex3D(x * frequency, y * frequency, z * frequency);
            maxValue += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }
    
    public static float ridgeNoise2D(float x, float y, int octaves, float lacunarity, float gain) {
        float value = 0;
        float amplitude = 1;
        float frequency = 1;
        float maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            float n = 1 - MathHelper.abs(simplex2D(x * frequency, y * frequency));
            n = n * n;
            value += n * amplitude;
            maxValue += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }
    
    public static float billowNoise2D(float x, float y, int octaves, float lacunarity, float gain) {
        float value = 0;
        float amplitude = 1;
        float frequency = 1;
        float maxValue = 0;
        for (int i = 0; i < octaves; i++) {
            float n = MathHelper.abs(simplex2D(x * frequency, y * frequency));
            value += n * amplitude;
            maxValue += amplitude;
            amplitude *= gain;
            frequency *= lacunarity;
        }
        return value / maxValue;
    }
    
    public static float valueNoise2D(float x, float y) {
        int xi = MathHelper.floorToInt(x);
        int yi = MathHelper.floorToInt(y);
        float xf = x - xi;
        float yf = y - yi;
        float a = permNoise(xi, yi);
        float b = permNoise(xi + 1, yi);
        float c = permNoise(xi, yi + 1);
        float d = permNoise(xi + 1, yi + 1);
        xf = xf * xf * (3 - 2 * xf);
        yf = yf * yf * (3 - 2 * yf);
        return MathHelper.lerp(MathHelper.lerp(a, b, xf), MathHelper.lerp(c, d, xf), yf);
    }
    
    public static float valueNoise3D(float x, float y, float z) {
        int xi = MathHelper.floorToInt(x);
        int yi = MathHelper.floorToInt(y);
        int zi = MathHelper.floorToInt(z);
        float xf = x - xi;
        float yf = y - yi;
        float zf = z - zi;
        xf = xf * xf * (3 - 2 * xf);
        yf = yf * yf * (3 - 2 * yf);
        zf = zf * zf * (3 - 2 * zf);
        return MathHelper.trilinearInterp(
            permNoise(xi, yi, zi), permNoise(xi + 1, yi, zi),
            permNoise(xi, yi + 1, zi), permNoise(xi + 1, yi + 1, zi),
            permNoise(xi, yi, zi + 1), permNoise(xi + 1, yi, zi + 1),
            permNoise(xi, yi + 1, zi + 1), permNoise(xi + 1, yi + 1, zi + 1),
            xf, yf, zf
        );
    }
    
    public static float[][] noiseMap2D(int width, int height, float scale, int seed) {
        float[][] map = new float[height][width];
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int s = seed != 0 ? seed : rand.nextInt();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                map[y][x] = simplex2D((x + s) / scale, (y + s) / scale);
            }
        }
        return map;
    }
    
    public static float[][] noiseMap2DParallel(int width, int height, float scale, int seed) {
        float[][] map = new float[height][width];
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        int s = seed != 0 ? seed : rand.nextInt();
        int chunkSize = Math.max(16, height / THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int start = 0; start < height; start += chunkSize) {
            int end = Math.min(start + chunkSize, height);
            int finalStart = start;
            int finalS = s;
            futures.add(com.lightlybyte.arsenic.threading.ThreadManager.getInstance().submit(() -> {
                for (int y = finalStart; y < end; y++) {
                    for (int x = 0; x < width; x++) {
                        map[y][x] = simplex2D((x + finalS) / scale, (y + finalS) / scale);
                    }
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return map;
    }
    
    private static float permNoise(int x, int y) {
        return GRAD[PERM[x + PERM[y & 255] & 255] & 255];
    }
    
    private static float permNoise(int x, int y, int z) {
        return GRAD_3D[(PERM_3D[x + PERM_3D[y + PERM_3D[z & 255] & 255] & 255] & 255) * 3];
    }
    
    private static float grad2D(int i, int j, float x, float y) {
        int idx = PERM[i + PERM[j & 255] & 255] & 255;
        float g = GRAD[idx];
        return g * x + (1 - MathHelper.abs(g)) * y;
    }
    
    private static float grad3D(int i, int j, int k, float x, float y, float z) {
        int idx = (PERM_3D[i + PERM_3D[j + PERM_3D[k & 255] & 255] & 255] & 255) * 3;
        return GRAD_3D[idx] * x + GRAD_3D[idx + 1] * y + GRAD_3D[idx + 2] * z;
    }
}