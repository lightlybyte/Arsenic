package com.lightlybyte.arsenic.math;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.ArrayList;
import java.util.List;

/**
 * Advanced Frustum implementation with SIMD-like optimizations, 
 * hierarchical culling, and comprehensive plane management.
 * 
 * Supports:
 * - Fast plane extraction using optimized matrix math
 * - AABB, OBB, sphere, and point culling
 * - Hierarchical frustum culling with LOD support
 * - Multithreaded batch culling
 * - Early exit optimizations
 * - Plane caching and dirty tracking
 * - View frustum split for shadow cascades
 */
public class Frustum {
    public static final int PLANE_LEFT = 0;
    public static final int PLANE_RIGHT = 1;
    public static final int PLANE_BOTTOM = 2;
    public static final int PLANE_TOP = 3;
    public static final int PLANE_NEAR = 4;
    public static final int PLANE_FAR = 5;
    public static final int PLANE_COUNT = 6;
    
    private final Vector4f[] planes = new Vector4f[PLANE_COUNT];
    private final Vector3f[] planeNormals = new Vector3f[PLANE_COUNT];
    private final float[] planeDistances = new float[PLANE_COUNT];
    private final float[] fastPlanes = new float[PLANE_COUNT * 4];
    
    private final Vector3f[] corners = new Vector3f[8];
    private final Vector3f[] farCorners = new Vector3f[4];
    private final Vector3f[] nearCorners = new Vector3f[4];
    
    private final float[] projectionMatrix = new float[16];
    private final float[] viewMatrix = new float[16];
    private final float[] clipMatrix = new float[16];
    
    private float near = 0.1f;
    private float far = 100.0f;
    private float fov = 70.0f;
    private float aspect = 1.333f;
    
    private boolean dirty = true;
    private boolean initialized = false;
    
    private long lastUpdateTime = 0;
    private int cullCalls = 0;
    private int cullHits = 0;
    private long totalCullTime = 0;
    
    private static final int THREADS = Runtime.getRuntime().availableProcessors();
    
    public Frustum() {
        for (int i = 0; i < PLANE_COUNT; i++) {
            planes[i] = new Vector4f();
            planeNormals[i] = new Vector3f();
        }
        for (int i = 0; i < 8; i++) {
            corners[i] = new Vector3f();
        }
        for (int i = 0; i < 4; i++) {
            farCorners[i] = new Vector3f();
            nearCorners[i] = new Vector3f();
        }
    }
    
    /**
     * Updates the frustum from projection and view matrices.
     * Uses optimized plane extraction with SIMD-like operations.
     */
    public void update(Matrix4f projection, Matrix4f view) {
        long startTime = System.nanoTime();
        
        projection.get(projectionMatrix);
        view.get(viewMatrix);
        FastMatrix.mul(projectionMatrix, viewMatrix, clipMatrix);
        
        extractPlane(clipMatrix, 0, 3, PLANE_LEFT);   // Left
        extractPlane(clipMatrix, 1, 3, PLANE_RIGHT);  // Right
        extractPlane(clipMatrix, 2, 3, PLANE_BOTTOM); // Bottom
        extractPlane(clipMatrix, 3, 3, PLANE_TOP);    // Top
        extractPlane(clipMatrix, 4, 3, PLANE_NEAR);   // Near
        extractPlane(clipMatrix, 5, 3, PLANE_FAR);    // Far
        
        computeCorners();
        computePlaneData();
        
        dirty = false;
        initialized = true;
        lastUpdateTime = System.nanoTime() - startTime;
    }
    
    /**
     * Updates using pre-computed clip matrix for speed.
     */
    public void updateFromClip(Matrix4f clip) {
        float[] m = FastMatrix.toArray(clip);
        System.arraycopy(m, 0, clipMatrix, 0, 16);
        
        extractPlane(m, 0, 3, PLANE_LEFT);
        extractPlane(m, 1, 3, PLANE_RIGHT);
        extractPlane(m, 2, 3, PLANE_BOTTOM);
        extractPlane(m, 3, 3, PLANE_TOP);
        extractPlane(m, 4, 3, PLANE_NEAR);
        extractPlane(m, 5, 3, PLANE_FAR);
        
        computeCorners();
        computePlaneData();
        dirty = false;
        initialized = true;
    }
    
    /**
     * Extracts a plane from the clip matrix with full normalization.
     */
    private void extractPlane(float[] m, int row, int col, int idx) {
        int base = idx * 4;
        fastPlanes[base] = m[col + row * 4] + m[col + 3 * 4];
        fastPlanes[base + 1] = m[row + 1 * 4] + m[3 * 4 + 1];
        fastPlanes[base + 2] = m[row + 2 * 4] + m[3 * 4 + 2];
        fastPlanes[base + 3] = m[row + 3 * 4] + m[3 * 4 + 3];
        
        float len = MathHelper.fastInvSqrt(
            fastPlanes[base] * fastPlanes[base] +
            fastPlanes[base + 1] * fastPlanes[base + 1] +
            fastPlanes[base + 2] * fastPlanes[base + 2]
        );
        fastPlanes[base] *= len;
        fastPlanes[base + 1] *= len;
        fastPlanes[base + 2] *= len;
        fastPlanes[base + 3] *= len;
        
        planes[idx].set(fastPlanes[base], fastPlanes[base + 1], fastPlanes[base + 2], fastPlanes[base + 3]);
    }
    
    /**
     * Extracts a plane from the clip matrix without normalization (faster).
     */
    private void extractPlaneFast(float[] m, int row, int col, int idx) {
        int base = idx * 4;
        fastPlanes[base] = m[col + row * 4] + m[col + 3 * 4];
        fastPlanes[base + 1] = m[row + 1 * 4] + m[3 * 4 + 1];
        fastPlanes[base + 2] = m[row + 2 * 4] + m[3 * 4 + 2];
        fastPlanes[base + 3] = m[row + 3 * 4] + m[3 * 4 + 3];
        planes[idx].set(fastPlanes[base], fastPlanes[base + 1], fastPlanes[base + 2], fastPlanes[base + 3]);
    }
    
    private void computePlaneData() {
        for (int i = 0; i < PLANE_COUNT; i++) {
            int base = i * 4;
            planeNormals[i].set(fastPlanes[base], fastPlanes[base + 1], fastPlanes[base + 2]);
            planeDistances[i] = fastPlanes[base + 3];
        }
    }
    
    private void computeCorners() {
        // Near plane corners
        float nearH = (float) (Math.tan(Math.toRadians(fov) / 2.0) * near);
        float nearW = nearH * aspect;
        
        nearCorners[0].set(-nearW, -nearH, -near);
        nearCorners[1].set(nearW, -nearH, -near);
        nearCorners[2].set(nearW, nearH, -near);
        nearCorners[3].set(-nearW, nearH, -near);
        
        // Far plane corners
        float farH = (float) (Math.tan(Math.toRadians(fov) / 2.0) * far);
        float farW = farH * aspect;
        
        farCorners[0].set(-farW, -farH, -far);
        farCorners[1].set(farW, -farH, -far);
        farCorners[2].set(farW, farH, -far);
        farCorners[3].set(-farW, farH, -far);
        
        // Combine all corners
        System.arraycopy(nearCorners, 0, corners, 0, 4);
        System.arraycopy(farCorners, 0, corners, 4, 4);
    }
    
    /**
     * Tests if an AABB is visible with early exit optimization.
     */
    public boolean isBoxVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (!initialized) return true;
        cullCalls++;
        long start = System.nanoTime();
        
        // Quick sphere check first (faster)
        float cx = (minX + maxX) * 0.5f;
        float cy = (minY + maxY) * 0.5f;
        float cz = (minZ + maxZ) * 0.5f;
        float radius = MathHelper.fastSqrt(
            (maxX - minX) * (maxX - minX) +
            (maxY - minY) * (maxY - minY) +
            (maxZ - minZ) * (maxZ - minZ)
        ) * 0.5f;
        
        for (int i = 0; i < PLANE_COUNT; i++) {
            int base = i * 4;
            float dist = fastPlanes[base] * cx + fastPlanes[base + 1] * cy + fastPlanes[base + 2] * cz + fastPlanes[base + 3];
            if (dist < -radius) {
                totalCullTime += System.nanoTime() - start;
                return false;
            }
        }
        
        // Exact AABB check for uncertain cases
        for (int i = 0; i < PLANE_COUNT; i++) {
            int base = i * 4;
            float px = fastPlanes[base] >= 0 ? maxX : minX;
            float py = fastPlanes[base + 1] >= 0 ? maxY : minY;
            float pz = fastPlanes[base + 2] >= 0 ? maxZ : minZ;
            if (fastPlanes[base] * px + fastPlanes[base + 1] * py + fastPlanes[base + 2] * pz + fastPlanes[base + 3] < 0) {
                totalCullTime += System.nanoTime() - start;
                return false;
            }
        }
        
        cullHits++;
        totalCullTime += System.nanoTime() - start;
        return true;
    }
    
    /**
     * Tests if an OBB (oriented bounding box) is visible.
     */
    public boolean isOBBVisible(float[] center, float[] halfExtents, float[] rotation) {
        if (!initialized) return true;
        cullCalls++;
        
        for (int i = 0; i < PLANE_COUNT; i++) {
            float[] axis = new float[]{fastPlanes[i * 4], fastPlanes[i * 4 + 1], fastPlanes[i * 4 + 2]};
            float r = halfExtents[0] * MathHelper.abs(axis[0] * rotation[0] + axis[1] * rotation[3] + axis[2] * rotation[6])
                    + halfExtents[1] * MathHelper.abs(axis[0] * rotation[1] + axis[1] * rotation[4] + axis[2] * rotation[7])
                    + halfExtents[2] * MathHelper.abs(axis[0] * rotation[2] + axis[1] * rotation[5] + axis[2] * rotation[8]);
            float dist = axis[0] * center[0] + axis[1] * center[1] + axis[2] * center[2] + fastPlanes[i * 4 + 3];
            if (dist < -r) return false;
        }
        return true;
    }
    
    /**
     * Tests if a sphere is visible.
     */
    public boolean isSphereVisible(float cx, float cy, float cz, float radius) {
        if (!initialized) return true;
        cullCalls++;
        long start = System.nanoTime();
        
        for (int i = 0; i < PLANE_COUNT; i++) {
            int base = i * 4;
            float dist = fastPlanes[base] * cx + fastPlanes[base + 1] * cy + fastPlanes[base + 2] * cz + fastPlanes[base + 3];
            if (dist < -radius) {
                totalCullTime += System.nanoTime() - start;
                return false;
            }
        }
        
        cullHits++;
        totalCullTime += System.nanoTime() - start;
        return true;
    }
    
    /**
     * Tests if a point is visible.
     */
    public boolean isPointVisible(float x, float y, float z) {
        if (!initialized) return true;
        
        for (int i = 0; i < PLANE_COUNT; i++) {
            int base = i * 4;
            float dist = fastPlanes[base] * x + fastPlanes[base + 1] * y + fastPlanes[base + 2] * z + fastPlanes[base + 3];
            if (dist < 0) return false;
        }
        return true;
    }
    
    /**
     * Batch culls multiple AABBs using multithreading.
     */
    public boolean[] cullBoxesParallel(float[] boxes, int count) {
        if (!initialized || count == 0) return new boolean[0];
        
        boolean[] results = new boolean[count];
        if (count < 16) {
            for (int i = 0; i < count; i++) {
                int base = i * 6;
                results[i] = isBoxVisible(
                    boxes[base], boxes[base + 1], boxes[base + 2],
                    boxes[base + 3], boxes[base + 4], boxes[base + 5]
                );
            }
            return results;
        }
        
        int chunkSize = Math.max(16, count / THREADS);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        float[] localBoxes = boxes.clone();
        
        for (int start = 0; start < count; start += chunkSize) {
            int end = Math.min(start + chunkSize, count);
            int finalStart = start;
            int finalEnd = end;
            
            futures.add(com.lightlybyte.arsenic.threading.ThreadManager.getInstance().submit(() -> {
                for (int i = finalStart; i < finalEnd; i++) {
                    int base = i * 6;
                    results[i] = isBoxVisible(
                        localBoxes[base], localBoxes[base + 1], localBoxes[base + 2],
                        localBoxes[base + 3], localBoxes[base + 4], localBoxes[base + 5]
                    );
                }
            }));
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return results;
    }
    
    /**
     * Hierarchical culling for octree-like structures.
     * Tests parent bounds first, then children only if parent is visible.
     */
    public boolean isNodeVisible(float minX, float minY, float minZ, float maxX, float maxY, float maxZ,
                                  float childMinX, float childMinY, float childMinZ,
                                  float childMaxX, float childMaxY, float childMaxZ) {
        if (!isBoxVisible(minX, minY, minZ, maxX, maxY, maxZ)) {
            return false;
        }
        return isBoxVisible(childMinX, childMinY, childMinZ, childMaxX, childMaxY, childMaxZ);
    }
    
    /**
     * Shadow frustum culling for cascaded shadow maps.
     * Returns culling result against a shadow frustum split.
     */
    public ShadowCullResult cullForShadow(float[] splitPlanes, float minX, float minY, float minZ,
                                          float maxX, float maxY, float maxZ) {
        ShadowCullResult result = new ShadowCullResult();
        
        for (int i = 0; i < splitPlanes.length / 4; i++) {
            int base = i * 4;
            float px = splitPlanes[base] >= 0 ? maxX : minX;
            float py = splitPlanes[base + 1] >= 0 ? maxY : minY;
            float pz = splitPlanes[base + 2] >= 0 ? maxZ : minZ;
            if (splitPlanes[base] * px + splitPlanes[base + 1] * py + splitPlanes[base + 2] * pz + splitPlanes[base + 3] < 0) {
                result.isVisible = false;
                result.cascadeIndex = i;
                return result;
            }
        }
        
        result.isVisible = true;
        result.cascadeIndex = splitPlanes.length / 4 - 1;
        return result;
    }
    
    /**
     * Computes the bounding sphere of the frustum.
     */
    public BoundingSphere getBoundingSphere() {
        float cx = 0, cy = 0, cz = 0;
        for (Vector3f corner : corners) {
            cx += corner.x;
            cy += corner.y;
            cz += corner.z;
        }
        cx /= 8;
        cy /= 8;
        cz /= 8;
        
        float maxDist = 0;
        for (Vector3f corner : corners) {
            float dist = MathHelper.distanceSq(cx, cy, cz, corner.x, corner.y, corner.z);
            if (dist > maxDist) maxDist = dist;
        }
        
        return new BoundingSphere(cx, cy, cz, MathHelper.sqrt(maxDist));
    }
    
    /**
     * Splits the frustum into multiple sub-frusta for cascaded shadow maps.
     */
    public Frustum[] splitForCascades(int count, float splitLambda) {
        Frustum[] splits = new Frustum[count];
        
        for (int i = 0; i < count; i++) {
            float ratio = (i + 1) / (float) count;
            float nearSplit = near * (float) Math.pow(far / near, ratio * splitLambda + (1 - splitLambda) * ratio);
            float farSplit = (i + 1 < count) ? 
                near * (float) Math.pow(far / near, (i + 2) / (float) count * splitLambda + (1 - splitLambda) * (i + 2) / (float) count) : 
                far;
            
            splits[i] = createSubFrustum(nearSplit, farSplit);
        }
        
        return splits;
    }
    
    /**
     * Creates a sub-frustum with custom near/far planes.
     */
    public Frustum createSubFrustum(float near, float far) {
        Frustum sub = new Frustum();
        sub.near = near;
        sub.far = far;
        sub.fov = fov;
        sub.aspect = aspect;
        
        Matrix4f proj = new Matrix4f();
        proj.setPerspective((float) Math.toRadians(fov), aspect, near, far);
        Matrix4f view = FastMatrix.toMatrix(viewMatrix);
        sub.update(proj, view);
        
        return sub;
    }
    
    /**
     * Gets the volume of the frustum for LOD calculations.
     */
    public float getVolume() {
        float farVolume = (float) (Math.tan(Math.toRadians(fov) / 2.0) * far);
        float nearVolume = (float) (Math.tan(Math.toRadians(fov) / 2.0) * near);
        return (farVolume * farVolume * far - nearVolume * nearVolume * near) * aspect / 3.0f;
    }
    
    /**
     * Calculates LOD based on distance from frustum center.
     */
    public int getLOD(float cx, float cy, float cz, int maxLOD) {
        float dist = MathHelper.distance(cx, cy, cz, 0, 0, 0);
        float maxDist = far;
        float ratio = MathHelper.clamp(dist / maxDist, 0, 1);
        return Math.min(maxLOD, (int) (ratio * maxLOD));
    }
    
    /**
     * Clears culling statistics.
     */
    public void resetStats() {
        cullCalls = 0;
        cullHits = 0;
        totalCullTime = 0;
    }
    
    // ==================== GETTERS ====================
    
    public Vector4f[] getPlanes() {
        return planes;
    }
    
    public float[] getFastPlanes() {
        return fastPlanes;
    }
    
    public Vector3f[] getPlaneNormals() {
        return planeNormals;
    }
    
    public float[] getPlaneDistances() {
        return planeDistances;
    }
    
    public Vector3f[] getCorners() {
        return corners;
    }
    
    public Vector3f[] getFarCorners() {
        return farCorners;
    }
    
    public Vector3f[] getNearCorners() {
        return nearCorners;
    }
    
    public float getNear() {
        return near;
    }
    
    public float getFar() {
        return far;
    }
    
    public float getFov() {
        return fov;
    }
    
    public float getAspect() {
        return aspect;
    }
    
    public boolean isDirty() {
        return dirty;
    }
    
    public boolean isInitialized() {
        return initialized;
    }
    
    public float[] getProjectionMatrix() {
        return projectionMatrix;
    }
    
    public float[] getViewMatrix() {
        return viewMatrix;
    }
    
    public float[] getClipMatrix() {
        return clipMatrix;
    }
    
    public long getLastUpdateTime() {
        return lastUpdateTime;
    }
    
    public int getCullCalls() {
        return cullCalls;
    }
    
    public int getCullHits() {
        return cullHits;
    }
    
    public float getCullRate() {
        return cullCalls == 0 ? 0 : (float) cullHits / cullCalls;
    }
    
    public long getTotalCullTime() {
        return totalCullTime;
    }
    
    public long getAverageCullTime() {
        return cullCalls == 0 ? 0 : totalCullTime / cullCalls;
    }
    
    // ==================== SETTERS ====================
    
    public void setNear(float near) {
        this.near = near;
        dirty = true;
    }
    
    public void setFar(float far) {
        this.far = far;
        dirty = true;
    }
    
    public void setFov(float fov) {
        this.fov = fov;
        dirty = true;
    }
    
    public void setAspect(float aspect) {
        this.aspect = aspect;
        dirty = true;
    }
    
    public void markDirty() {
        dirty = true;
    }
    
    // ==================== INNER CLASSES ====================
    
    public static class BoundingSphere {
        public float x, y, z, radius;
        
        public BoundingSphere(float x, float y, float z, float radius) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
        }
        
        public boolean intersects(Frustum frustum) {
            return frustum.isSphereVisible(x, y, z, radius);
        }
    }
    
    public static class ShadowCullResult {
        public boolean isVisible = false;
        public int cascadeIndex = 0;
        
        @Override
        public String toString() {
            return "ShadowCullResult{visible=" + isVisible + ", cascade=" + cascadeIndex + "}";
        }
    }
    
    // ==================== DIAGNOSTICS ====================
    
    public String getDiagnostics() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Frustum Diagnostics ===\n");
        sb.append("Initialized: ").append(initialized).append("\n");
        sb.append("Dirty: ").append(dirty).append("\n");
        sb.append("Near: ").append(near).append("\n");
        sb.append("Far: ").append(far).append("\n");
        sb.append("FOV: ").append(fov).append("\n");
        sb.append("Aspect: ").append(aspect).append("\n");
        sb.append("Volume: ").append(getVolume()).append("\n");
        sb.append("Update time: ").append(lastUpdateTime).append("ns\n");
        sb.append("Cull calls: ").append(cullCalls).append("\n");
        sb.append("Cull hits: ").append(cullHits).append("\n");
        sb.append("Cull rate: ").append(String.format("%.2f%%", getCullRate() * 100)).append("\n");
        sb.append("Avg cull time: ").append(getAverageCullTime()).append("ns\n");
        sb.append("Total cull time: ").append(totalCullTime).append("ns\n");
        sb.append("\nPlanes:\n");
        for (int i = 0; i < PLANE_COUNT; i++) {
            int base = i * 4;
            sb.append("  P").append(i).append(": (")
              .append(String.format("%.3f", fastPlanes[base])).append(", ")
              .append(String.format("%.3f", fastPlanes[base + 1])).append(", ")
              .append(String.format("%.3f", fastPlanes[base + 2])).append(", ")
              .append(String.format("%.3f", fastPlanes[base + 3])).append(")\n");
        }
        sb.append("\nCorners:\n");
        for (int i = 0; i < 8; i++) {
            sb.append("  C").append(i).append(": (")
              .append(String.format("%.2f", corners[i].x)).append(", ")
              .append(String.format("%.2f", corners[i].y)).append(", ")
              .append(String.format("%.2f", corners[i].z)).append(")\n");
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("Frustum{planes=%d, corners=%d, near=%.2f, far=%.2f, fov=%.2f, volume=%.2f}",
            PLANE_COUNT, 8, near, far, fov, getVolume());
    }
}