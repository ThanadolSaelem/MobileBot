/**
 * @Author 
 * @AIDE AIDE+
*/
package com.cfks.goosedroid.SamEngine;


public class Vector2
 {
    // Define x and y coordinates
    public float x;
    public float y;

    // Define zero vector
    public static final Vector2 zero = new Vector2(0f, 0f);

    // Constructor
    public Vector2(float _x, float _y) {
        this.x = _x;
        this.y = _y;
    }

    // Vector addition
    public static Vector2 add(Vector2 a, Vector2 b) {
        return new Vector2(a.x + b.x, a.y + b.y);
    }

    // Vector subtraction
    public static Vector2 subtract(Vector2 a, Vector2 b) {
        return new Vector2(a.x - b.x, a.y - b.y);
    }

    // Vector negation
    public static Vector2 negate(Vector2 a) {
        return new Vector2(-a.x, -a.y);
    }

    // Vector-vector multiplication
    public static Vector2 multiply(Vector2 a, Vector2 b) {
        return new Vector2(a.x * b.x, a.y * b.y);
    }

    // Vector-scalar multiplication
    public static Vector2 multiply(Vector2 a, float b) {
        return new Vector2(a.x * b, a.y * b);
    }

    // Vector-scalar division
    public static Vector2 divide(Vector2 a, float b) {
        return new Vector2(a.x / b, a.y / b);
    }

    // Get unit vector from angle
    public static Vector2 GetFromAngleDegrees(float angle) {
        float radians = angle * 0.0174532924f;  // Convert degrees to radians
        return new Vector2((float)Math.cos(radians), (float)Math.sin(radians));
    }

    // Calculate distance between two vectors
    public static float Distance(Vector2 a, Vector2 b) {
        Vector2 diff = new Vector2(a.x - b.x, a.y - b.y);
        return (float)Math.sqrt(diff.x * diff.x + diff.y * diff.y);
    }

    // Linear interpolation
    public static Vector2 Lerp(Vector2 a, Vector2 b, float p) {
        return new Vector2(SamMath.Lerp(a.x, b.x, p), SamMath.Lerp(a.y, b.y, p));
    }

    // Calculate dot product
    public static float Dot(Vector2 a, Vector2 b) {
        return a.x * b.x + a.y * b.y;
    }

    // Vector normalization
    public static Vector2 Normalize(Vector2 a) {
        if (a.x == 0f && a.y == 0f) {
            return Vector2.zero;  // Prevent division by zero
        }
        float magnitude = (float)Math.sqrt(a.x * a.x + a.y * a.y);
        return new Vector2(a.x / magnitude, a.y / magnitude);
    }

    // Calculate vector magnitude
    public static float Magnitude(Vector2 a) {
        return (float)Math.sqrt(a.x * a.x + a.y * a.y);
    }
}

