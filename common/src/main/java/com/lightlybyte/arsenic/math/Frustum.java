package com.lightlybyte.arsenic.math;

import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Represents a viewing frustum for culling purposes.
 * Uses the plane-based frustum definition.
 */
public class Frustum {
    // Six planes of the frustum: Left, Right, Bottom, Top, Near, Far
    private final Vector4f[] planes = new Vector4f[6];
    
    public Frustum() {
        for (int i = 0; i < 6; i++) {
            planes[i] = new Vector4f();
        }
    }
    
    /**
     * Updates the frustum planes from the projection and view matrices.
     * This should be called every frame when the camera moves.
     */
    public void update(Matrix4f projectionMatrix, Matrix4f viewMatrix) {
        // Combine projection and view into a single matrix
        Matrix4f clipMatrix = new Matrix4f();
        projectionMatrix.mul(viewMatrix, clipMatrix);
        
        // Extract the six planes from the clip matrix
        // Left plane: row 3 + row 0
        planes[0].set(
            clipMatrix.m00() + clipMatrix.m30(),
            clipMatrix.m01() + clipMatrix.m31(),
            clipMatrix.m02() + clipMatrix.m32(),
            clipMatrix.m03() + clipMatrix.m33()
        );
        normalizePlane(planes[0]);
        
        // Right plane: row 3 - row 0
        planes[1].set(
            clipMatrix.m30() - clipMatrix.m00(),
            clipMatrix.m31() - clipMatrix.m01(),
            clipMatrix.m32() - clipMatrix.m02(),
            clipMatrix.m33() - clipMatrix.m03()
        );
        normalizePlane(planes[1]);
        
        // Bottom plane: row 3 + row 1
        planes[2].set(
            clipMatrix.m10() + clipMatrix.m30(),
            clipMatrix.m11() + clipMatrix.m31(),
            clipMatrix.m12() + clipMatrix.m32(),
            clipMatrix.m13() + clipMatrix.m33()
        );
        normalizePlane(planes[2]);
        
        // Top plane: row 3 - row 1
        planes[3].set(
            clipMatrix.m30() - clipMatrix.m10(),
            clipMatrix.m31() - clipMatrix.m11(),
            clipMatrix.m32() - clipMatrix.m12(),
            clipMatrix.m33() - clipMatrix.m13()
        );
        normalizePlane(planes[3]);
        
        // Near plane: row 3 + row 2
        planes[4].set(
            clipMatrix.m20() + clipMatrix.m30(),
            clipMatrix.m21() + clipMatrix.m31(),
            clipMatrix.m22() + clipMatrix.m32(),
            clipMatrix.m23() + clipMatrix.m33()
        );
        normalizePlane(planes[4]);
        
        // Far plane: row 3 - row 2
        planes[5].set(
            clipMatrix.m30() - clipMatrix.m20(),
            clipMatrix.m31() - clipMatrix.m21(),
            clipMatrix.m32() - clipMatrix.m22(),
            clipMatrix.m33() - clipMatrix.m23()
        );
        normalizePlane(planes[5]);
    }
    
    private void normalizePlane(Vector4f plane) {
        float length = (float) Math.sqrt(
            plane.x() * plane.x() + 
            plane.y() * plane.y() + 
            plane.z() * plane.z()
        );
        if (length > 0) {
            // JOML Vector4f uses .x, .y, .z, .w as direct fields
            // So we just assign directly
            plane.x = plane.x() / length;
            plane.y = plane.y() / length;
            plane.z = plane.z() / length;
            plane.w = plane.w() / length;
        }
    }
    
    /**
     * Tests if an AABB (axis-aligned bounding box) is inside the frustum.
     */
    public boolean isBoxVisible(float minX, float minY, float minZ, 
                                 float maxX, float maxY, float maxZ) {
        for (Vector4f plane : planes) {
            float pX = plane.x() >= 0 ? maxX : minX;
            float pY = plane.y() >= 0 ? maxY : minY;
            float pZ = plane.z() >= 0 ? maxZ : minZ;
            
            float distance = plane.x() * pX + plane.y() * pY + plane.z() * pZ + plane.w();
            if (distance < 0) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Tests if a sphere is inside the frustum.
     */
    public boolean isSphereVisible(float centerX, float centerY, float centerZ, float radius) {
        for (Vector4f plane : planes) {
            float distance = plane.x() * centerX + plane.y() * centerY + plane.z() * centerZ + plane.w();
            if (distance < -radius) {
                return false;
            }
        }
        return true;
    }
}