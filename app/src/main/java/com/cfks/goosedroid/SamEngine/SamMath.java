/**
 * @Author 
 * @AIDE AIDE+
*/
package com.cfks.goosedroid.SamEngine;
import java.util.*;

public class SamMath {

    // Constants
    public static final float Deg2Rad = 0.0174532924f;
    public static final float Rad2Deg = 57.2957764f;

    // Random object
    public static Random Rand = new Random();

    // RandomRange function equivalent to C# version
    public static float RandomRange(float min, float max) {
        return min + Rand.nextFloat() * (max - min);
    }

    // Lerp function equivalent to C# version
    public static float Lerp(float a, float b, float p) {
        return a * (1f - p) + b * p;
    }

    // Clamp function equivalent to C# version
    public static float Clamp(float a, float min, float max) {
        return Math.min(Math.max(a, min), max);
    }

    // LerpAngle - interpolate between angles handling wraparound
    public static float LerpAngle(float a, float b, float t) {
        float delta = ((b - a + 540f) % 360f) - 180f;
        return a + delta * Clamp(t, 0f, 1f);
    }

    // Abs function
    public static float Abs(float value) {
        return Math.abs(value);
    }

    // Sign function
    public static float Sign(float value) {
        return Math.signum(value);
    }

    // MoveTowards - move a value towards target by maxDelta
    public static float MoveTowards(float current, float target, float maxDelta) {
        if (Math.abs(target - current) <= maxDelta) {
            return target;
        }
        return current + Math.signum(target - current) * maxDelta;
    }

    // SmoothStep - smooth interpolation
    public static float SmoothStep(float from, float to, float t) {
        t = Clamp(t, 0f, 1f);
        t = t * t * (3f - 2f * t);
        return from + (to - from) * t;
    }

    // PingPong - oscillate value between 0 and length
    public static float PingPong(float t, float length) {
        t = t % (length * 2f);
        return length - Math.abs(t - length);
    }
}

