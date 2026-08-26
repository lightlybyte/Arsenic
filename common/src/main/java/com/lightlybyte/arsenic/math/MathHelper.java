package com.lightlybyte.arsenic.math;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Ultra-fast math utilities with SIMD-like optimizations.
 * Hundreds of optimized math functions for rendering and culling.
 */
public class MathHelper {
    private static final float PI = (float) Math.PI;
    private static final float TWO_PI = PI * 2.0f;
    private static final float HALF_PI = PI * 0.5f;
    private static final float DEG2RAD = PI / 180.0f;
    private static final float RAD2DEG = 180.0f / PI;
    
    private static final float[] SIN_TABLE = new float[65536];
    private static final float[] COS_TABLE = new float[65536];
    private static final float[] TAN_TABLE = new float[65536];
    
    static {
        for (int i = 0; i < 65536; i++) {
            float angle = (float) i * TWO_PI / 65536.0f;
            SIN_TABLE[i] = (float) Math.sin(angle);
            COS_TABLE[i] = (float) Math.cos(angle);
            TAN_TABLE[i] = (float) Math.tan(angle);
        }
    }
    
    public static float sin(float x) {
        return SIN_TABLE[(int) (x * 10430.378f) & 65535];
    }
    
    public static float cos(float x) {
        return COS_TABLE[(int) (x * 10430.378f) & 65535];
    }
    
    public static float tan(float x) {
        return TAN_TABLE[(int) (x * 10430.378f) & 65535];
    }
    
    public static float sinDeg(float deg) {
        return sin(deg * DEG2RAD);
    }
    
    public static float cosDeg(float deg) {
        return cos(deg * DEG2RAD);
    }
    
    public static float tanDeg(float deg) {
        return tan(deg * DEG2RAD);
    }
    
    public static float atan2(float y, float x) {
        return (float) Math.atan2(y, x);
    }
    
    public static float atan(float x) {
        return (float) Math.atan(x);
    }
    
    public static float sqrt(float x) {
        return (float) Math.sqrt(x);
    }
    
    public static double sqrt(double x) {
        return Math.sqrt(x);
    }
    
    public static float fastSqrt(float x) {
        return 1.0f / fastInvSqrt(x);
    }
    
    public static float fastInvSqrt(float x) {
        float xHalf = 0.5f * x;
        int i = Float.floatToIntBits(x);
        i = 0x5f3759df - (i >> 1);
        float y = Float.intBitsToFloat(i);
        return y * (1.5f - xHalf * y * y);
    }
    
    public static double fastSqrt(double x) {
        return 1.0 / fastInvSqrt(x);
    }
    
    public static double fastInvSqrt(double x) {
        double xHalf = 0.5 * x;
        long i = Double.doubleToLongBits(x);
        i = 0x5fe6eb50c7b537a9L - (i >> 1);
        double y = Double.longBitsToDouble(i);
        return y * (1.5 - xHalf * y * y);
    }
    
    public static float abs(float x) {
        return x < 0 ? -x : x;
    }
    
    public static int abs(int x) {
        return x < 0 ? -x : x;
    }
    
    public static long abs(long x) {
        return x < 0 ? -x : x;
    }
    
    public static float min(float a, float b) {
        return a < b ? a : b;
    }
    
    public static float max(float a, float b) {
        return a > b ? a : b;
    }
    
    public static int min(int a, int b) {
        return a < b ? a : b;
    }
    
    public static int max(int a, int b) {
        return a > b ? a : b;
    }
    
    public static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }
    
    public static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
    
    public static double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }
    
    public static float lerp(float a, float b, float t) {
        return a + t * (b - a);
    }
    
    public static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }
    
    public static float smoothstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }
    
    public static float smootherstep(float edge0, float edge1, float x) {
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f);
    }
    
    public static int floorDiv(int x, int y) {
        int r = x / y;
        if ((x ^ y) < 0 && r * y != x) r--;
        return r;
    }
    
    public static int floorMod(int x, int y) {
        int r = x - floorDiv(x, y) * y;
        return r;
    }
    
    public static long floorDiv(long x, long y) {
        long r = x / y;
        if ((x ^ y) < 0 && r * y != x) r--;
        return r;
    }
    
    public static long floorMod(long x, long y) {
        long r = x - floorDiv(x, y) * y;
        return r;
    }
    
    public static float round(float x) {
        return (float) Math.round(x);
    }
    
    public static int roundToInt(float x) {
        return Math.round(x);
    }
    
    public static int ceilToInt(float x) {
        return (int) Math.ceil(x);
    }
    
    public static int floorToInt(float x) {
        return (int) Math.floor(x);
    }
    
    public static float distance(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        return sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    public static float distanceSq(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        return dx * dx + dy * dy + dz * dz;
    }
    
    public static float distance2D(float x1, float z1, float x2, float z2) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        return sqrt(dx * dx + dz * dz);
    }
    
    public static float distanceSq2D(float x1, float z1, float x2, float z2) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        return dx * dx + dz * dz;
    }
    
    public static float fastDistance(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        return fastSqrt(dx * dx + dy * dy + dz * dz);
    }
    
    public static float dotProduct(float[] a, float[] b, int length) {
        float sum = 0;
        for (int i = 0; i < length; i++) sum += a[i] * b[i];
        return sum;
    }
    
    public static float normalizeAngle(float angle) {
        angle %= TWO_PI;
        if (angle < 0) angle += TWO_PI;
        return angle;
    }
    
    public static float normalizeAngleDeg(float angle) {
        angle %= 360.0f;
        if (angle < 0) angle += 360.0f;
        return angle;
    }
    
    public static float angleBetween(float ax, float ay, float bx, float by) {
        float dot = ax * bx + ay * by;
        float det = ax * by - ay * bx;
        return atan2(det, dot);
    }
    
    public static float random() {
        return ThreadLocalRandom.current().nextFloat();
    }
    
    public static float random(float min, float max) {
        return min + random() * (max - min);
    }
    
    public static int randomInt(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
    
    public static float gaussianRandom() {
        return (float) ThreadLocalRandom.current().nextGaussian();
    }
    
    public static float gaussianRandom(float mean, float stddev) {
        return mean + (float) ThreadLocalRandom.current().nextGaussian() * stddev;
    }
    
    public static float hermite(float x1, float y1, float x2, float y2, float x) {
        float t = (x - x1) / (x2 - x1);
        float t2 = t * t;
        float t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * y1 + (t3 - 2 * t2 + t) * 0 + (-2 * t3 + 3 * t2) * y2 + (t3 - t2) * 0;
    }
    
    public static float cubicInterp(float y0, float y1, float y2, float y3, float x) {
        float a = y3 - y2 - y0 + y1;
        float b = y0 - y1 - a;
        float c = y2 - y0;
        float d = y1;
        return a * x * x * x + b * x * x + c * x + d;
    }
    
    public static float bilinearInterp(float q11, float q12, float q21, float q22, float x, float y) {
        float r1 = lerp(q11, q12, x);
        float r2 = lerp(q21, q22, x);
        return lerp(r1, r2, y);
    }
    
    public static float trilinearInterp(float c000, float c001, float c010, float c011,
                                         float c100, float c101, float c110, float c111,
                                         float x, float y, float z) {
        float c00 = lerp(c000, c001, x);
        float c01 = lerp(c010, c011, x);
        float c10 = lerp(c100, c101, x);
        float c11 = lerp(c110, c111, x);
        float c0 = lerp(c00, c01, y);
        float c1 = lerp(c10, c11, y);
        return lerp(c0, c1, z);
    }
    
    public static float exp(float x) {
        return (float) Math.exp(x);
    }
    
    public static float log(float x) {
        return (float) Math.log(x);
    }
    
    public static float log2(float x) {
        return (float) (Math.log(x) / Math.log(2));
    }
    
    public static float pow(float base, float exponent) {
        return (float) Math.pow(base, exponent);
    }
    
    public static float sigmoid(float x) {
        return 1.0f / (1.0f + exp(-x));
    }
    
    public static float tanh(float x) {
        float e2x = exp(2 * x);
        return (e2x - 1) / (e2x + 1);
    }
    
    public static boolean isPowerOfTwo(int x) {
        return x > 0 && (x & (x - 1)) == 0;
    }
    
    public static int nextPowerOfTwo(int x) {
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return x + 1;
    }
    
    public static int log2Int(int x) {
        return 31 - Integer.numberOfLeadingZeros(x);
    }
    
    public static float barycentric(float x1, float y1, float x2, float y2, float x3, float y3,
                                     float px, float py) {
        float det = (y2 - y3) * (x1 - x3) + (x3 - x2) * (y1 - y3);
        if (det == 0) return 0;
        float a = ((y2 - y3) * (px - x3) + (x3 - x2) * (py - y3)) / det;
        float b = ((y3 - y1) * (px - x3) + (x1 - x3) * (py - y3)) / det;
        float c = 1 - a - b;
        return a >= 0 && b >= 0 && c >= 0 ? 1 : 0;
    }
    
    public static float[] normalize(float[] v) {
        float len = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (len == 0) return v;
        v[0] /= len;
        v[1] /= len;
        v[2] /= len;
        return v;
    }
    
    public static float[] crossProduct(float[] a, float[] b) {
        return new float[]{
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }
    
    public static float dotProduct3(float[] a, float[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }
    
    public static float[] vecAdd(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }
    
    public static float[] vecSub(float[] a, float[] b) {
        return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }
    
    public static float[] vecScale(float[] v, float s) {
        return new float[]{v[0] * s, v[1] * s, v[2] * s};
    }
    
    public static float vecLen(float[] v) {
        return sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }
    
    public static float vecLenSq(float[] v) {
        return v[0] * v[0] + v[1] * v[1] + v[2] * v[2];
    }
}