package com.lightlybyte.arsenic.math;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

/**
 * Ultra-fast matrix operations for frustum culling and rendering.
 * Hundreds of lines of optimized matrix math.
 */
public class FastMatrix {
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    private static final float[] IDENTITY = new float[]{
        1, 0, 0, 0,
        0, 1, 0, 0,
        0, 0, 1, 0,
        0, 0, 0, 1
    };
    
    public static float[] identity() {
        return IDENTITY.clone();
    }
    
    public static float[] mul(float[] a, float[] b, float[] out) {
        out[0] = a[0] * b[0] + a[1] * b[4] + a[2] * b[8] + a[3] * b[12];
        out[1] = a[0] * b[1] + a[1] * b[5] + a[2] * b[9] + a[3] * b[13];
        out[2] = a[0] * b[2] + a[1] * b[6] + a[2] * b[10] + a[3] * b[14];
        out[3] = a[0] * b[3] + a[1] * b[7] + a[2] * b[11] + a[3] * b[15];
        out[4] = a[4] * b[0] + a[5] * b[4] + a[6] * b[8] + a[7] * b[12];
        out[5] = a[4] * b[1] + a[5] * b[5] + a[6] * b[9] + a[7] * b[13];
        out[6] = a[4] * b[2] + a[5] * b[6] + a[6] * b[10] + a[7] * b[14];
        out[7] = a[4] * b[3] + a[5] * b[7] + a[6] * b[11] + a[7] * b[15];
        out[8] = a[8] * b[0] + a[9] * b[4] + a[10] * b[8] + a[11] * b[12];
        out[9] = a[8] * b[1] + a[9] * b[5] + a[10] * b[9] + a[11] * b[13];
        out[10] = a[8] * b[2] + a[9] * b[6] + a[10] * b[10] + a[11] * b[14];
        out[11] = a[8] * b[3] + a[9] * b[7] + a[10] * b[11] + a[11] * b[15];
        out[12] = a[12] * b[0] + a[13] * b[4] + a[14] * b[8] + a[15] * b[12];
        out[13] = a[12] * b[1] + a[13] * b[5] + a[14] * b[9] + a[15] * b[13];
        out[14] = a[12] * b[2] + a[13] * b[6] + a[14] * b[10] + a[15] * b[14];
        out[15] = a[12] * b[3] + a[13] * b[7] + a[14] * b[11] + a[15] * b[15];
        return out;
    }
    
    public static float[] mul(float[] a, float[] b) {
        return mul(a, b, new float[16]);
    }
    
    public static float[] mulParallel(float[] a, float[] b) {
        float[] out = new float[16];
        if (THREADS < 2) return mul(a, b, out);
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        int chunkSize = 4;
        for (int row = 0; row < 4; row++) {
            final int r = row;
            futures.add(com.lightlybyte.arsenic.threading.ThreadManager.getInstance().submit(() -> {
                int base = r * 4;
                for (int col = 0; col < 4; col++) {
                    float sum = 0;
                    for (int k = 0; k < 4; k++) {
                        sum += a[r * 4 + k] * b[k * 4 + col];
                    }
                    out[base + col] = sum;
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return out;
    }
    
    public static float[] toArray(Matrix4f matrix) {
        return new float[]{
            matrix.m00(), matrix.m01(), matrix.m02(), matrix.m03(),
            matrix.m10(), matrix.m11(), matrix.m12(), matrix.m13(),
            matrix.m20(), matrix.m21(), matrix.m22(), matrix.m23(),
            matrix.m30(), matrix.m31(), matrix.m32(), matrix.m33()
        };
    }
    
    public static Matrix4f toMatrix(float[] arr) {
        Matrix4f m = new Matrix4f();
        m.set(arr[0], arr[1], arr[2], arr[3],
              arr[4], arr[5], arr[6], arr[7],
              arr[8], arr[9], arr[10], arr[11],
              arr[12], arr[13], arr[14], arr[15]);
        return m;
    }
    
    public static float[] extractFrustumPlanes(Matrix4f clipMatrix) {
        float[] planes = new float[24];
        float[] m = toArray(clipMatrix);
        extractPlane(m, 0, 3, 0, planes);
        extractPlane(m, 1, 3, 1, planes);
        extractPlane(m, 2, 3, 2, planes);
        extractPlane(m, 3, 3, 3, planes);
        extractPlane(m, 4, 3, 4, planes);
        extractPlane(m, 5, 3, 5, planes);
        return planes;
    }
    
    public static float[][] extractFrustumPlanes(Matrix4f clipMatrix, boolean asArray) {
        float[][] planes = new float[6][4];
        float[] m = toArray(clipMatrix);
        for (int i = 0; i < 6; i++) {
            int row = i < 3 ? i : i + 1;
            int sign = i < 3 ? 1 : -1;
            int base = i * 4;
            planes[i][0] = (m[row * 4] + sign * m[3 * 4]) / 2;
            planes[i][1] = (m[row * 4 + 1] + sign * m[3 * 4 + 1]) / 2;
            planes[i][2] = (m[row * 4 + 2] + sign * m[3 * 4 + 2]) / 2;
            planes[i][3] = (m[row * 4 + 3] + sign * m[3 * 4 + 3]) / 2;
            float len = MathHelper.fastInvSqrt(planes[i][0] * planes[i][0] + planes[i][1] * planes[i][1] + planes[i][2] * planes[i][2]);
            planes[i][0] *= len;
            planes[i][1] *= len;
            planes[i][2] *= len;
            planes[i][3] *= len;
        }
        return planes;
    }
    
    private static void extractPlane(float[] m, int row, int col, int planeIndex, float[] out) {
        int base = planeIndex * 4;
        out[base] = m[col + row * 4] + m[col + 3 * 4];
        out[base + 1] = m[row + 1 * 4] + m[3 * 4 + 1];
        out[base + 2] = m[row + 2 * 4] + m[3 * 4 + 2];
        out[base + 3] = m[row + 3 * 4] + m[3 * 4 + 3];
        float len = MathHelper.fastInvSqrt(out[base] * out[base] + out[base + 1] * out[base + 1] + out[base + 2] * out[base + 2]);
        out[base] *= len;
        out[base + 1] *= len;
        out[base + 2] *= len;
        out[base + 3] *= len;
    }
    
    public static boolean isBoxVisible(float[] planes, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (int i = 0; i < 6; i++) {
            int base = i * 4;
            float px = planes[base] >= 0 ? maxX : minX;
            float py = planes[base + 1] >= 0 ? maxY : minY;
            float pz = planes[base + 2] >= 0 ? maxZ : minZ;
            if (planes[base] * px + planes[base + 1] * py + planes[base + 2] * pz + planes[base + 3] < 0) {
                return false;
            }
        }
        return true;
    }
    
    public static boolean isBoxVisible(float[][] planes, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        for (float[] p : planes) {
            float px = p[0] >= 0 ? maxX : minX;
            float py = p[1] >= 0 ? maxY : minY;
            float pz = p[2] >= 0 ? maxZ : minZ;
            if (p[0] * px + p[1] * py + p[2] * pz + p[3] < 0) return false;
        }
        return true;
    }
    
    public static boolean isSphereVisible(float[] planes, float cx, float cy, float cz, float radius) {
        for (int i = 0; i < 6; i++) {
            int base = i * 4;
            float dist = planes[base] * cx + planes[base + 1] * cy + planes[base + 2] * cz + planes[base + 3];
            if (dist < -radius) return false;
        }
        return true;
    }
    
    public static float[] createTranslation(float x, float y, float z) {
        float[] m = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }
    
    public static float[] createScale(float x, float y, float z) {
        float[] m = identity();
        m[0] = x;
        m[5] = y;
        m[10] = z;
        return m;
    }
    
    public static float[] createRotationX(float angle) {
        float[] m = identity();
        float c = MathHelper.cos(angle);
        float s = MathHelper.sin(angle);
        m[5] = c;
        m[6] = -s;
        m[9] = s;
        m[10] = c;
        return m;
    }
    
    public static float[] createRotationY(float angle) {
        float[] m = identity();
        float c = MathHelper.cos(angle);
        float s = MathHelper.sin(angle);
        m[0] = c;
        m[2] = s;
        m[8] = -s;
        m[10] = c;
        return m;
    }
    
    public static float[] createRotationZ(float angle) {
        float[] m = identity();
        float c = MathHelper.cos(angle);
        float s = MathHelper.sin(angle);
        m[0] = c;
        m[1] = -s;
        m[4] = s;
        m[5] = c;
        return m;
    }
    
    public static float[] createPerspective(float fov, float aspect, float near, float far) {
        float[] m = new float[16];
        float f = 1.0f / MathHelper.tan(fov / 2.0f);
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1;
        m[14] = (2 * far * near) / (near - far);
        return m;
    }
    
    public static float[] createOrthographic(float left, float right, float bottom, float top, float near, float far) {
        float[] m = identity();
        m[0] = 2.0f / (right - left);
        m[5] = 2.0f / (top - bottom);
        m[10] = -2.0f / (far - near);
        m[12] = -(right + left) / (right - left);
        m[13] = -(top + bottom) / (top - bottom);
        m[14] = -(far + near) / (far - near);
        return m;
    }
    
    public static float[] transpose(float[] m) {
        return new float[]{
            m[0], m[4], m[8], m[12],
            m[1], m[5], m[9], m[13],
            m[2], m[6], m[10], m[14],
            m[3], m[7], m[11], m[15]
        };
    }
    
    public static float determinant(float[] m) {
        return m[0] * (m[5] * m[10] - m[6] * m[9])
             - m[1] * (m[4] * m[10] - m[6] * m[8])
             + m[2] * (m[4] * m[9] - m[5] * m[8])
             + m[3] * (m[4] * m[9] - m[5] * m[8]);
    }
    
    public static float[] inverse(float[] m) {
        float[] inv = new float[16];
        float det = determinant(m);
        if (det == 0) return identity();
        inv[0] = (m[5] * m[10] - m[6] * m[9]) / det;
        inv[1] = (m[2] * m[9] - m[1] * m[10]) / det;
        inv[2] = (m[1] * m[6] - m[2] * m[5]) / det;
        inv[3] = 0;
        inv[4] = (m[6] * m[8] - m[4] * m[10]) / det;
        inv[5] = (m[0] * m[10] - m[2] * m[8]) / det;
        inv[6] = (m[2] * m[4] - m[0] * m[6]) / det;
        inv[7] = 0;
        inv[8] = (m[4] * m[9] - m[5] * m[8]) / det;
        inv[9] = (m[1] * m[8] - m[0] * m[9]) / det;
        inv[10] = (m[0] * m[5] - m[1] * m[4]) / det;
        inv[11] = 0;
        inv[12] = -(m[12] * inv[0] + m[13] * inv[4] + m[14] * inv[8]);
        inv[13] = -(m[12] * inv[1] + m[13] * inv[5] + m[14] * inv[9]);
        inv[14] = -(m[12] * inv[2] + m[13] * inv[6] + m[14] * inv[10]);
        inv[15] = 1;
        return inv;
    }
    
    public static float[] lookAt(float eyeX, float eyeY, float eyeZ,
                                   float centerX, float centerY, float centerZ,
                                   float upX, float upY, float upZ) {
        float[] f = {centerX - eyeX, centerY - eyeY, centerZ - eyeZ};
        float[] u = {upX, upY, upZ};
        float lenF = MathHelper.sqrt(f[0] * f[0] + f[1] * f[1] + f[2] * f[2]);
        f[0] /= lenF; f[1] /= lenF; f[2] /= lenF;
        float[] s = {f[1] * u[2] - f[2] * u[1], f[2] * u[0] - f[0] * u[2], f[0] * u[1] - f[1] * u[0]};
        float lenS = MathHelper.sqrt(s[0] * s[0] + s[1] * s[1] + s[2] * s[2]);
        s[0] /= lenS; s[1] /= lenS; s[2] /= lenS;
        u = new float[]{s[1] * f[2] - s[2] * f[1], s[2] * f[0] - s[0] * f[2], s[0] * f[1] - s[1] * f[0]};
        return new float[]{
            s[0], s[1], s[2], 0,
            u[0], u[1], u[2], 0,
            -f[0], -f[1], -f[2], 0,
            -(s[0] * eyeX + s[1] * eyeY + s[2] * eyeZ),
            -(u[0] * eyeX + u[1] * eyeY + u[2] * eyeZ),
            f[0] * eyeX + f[1] * eyeY + f[2] * eyeZ,
            1
        };
    }
}