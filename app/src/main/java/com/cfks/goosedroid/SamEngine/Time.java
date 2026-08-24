/**
 * @Author 
 * @AIDE AIDE+
*/
package com.cfks.goosedroid.SamEngine;

public class Time {
    // Define framerate and deltaTime constants
    public static final int framerate = 120;
    public static final float deltaTime = 0.008333334f;

    // Time tracking variables
    public static long timeStart;
    public static float time;

    static {
        // Initialize timer on startup
        timeStart = System.nanoTime();
        TickTime();
    }

    // Update time on each call
    public static void TickTime() {
        // Calculate elapsed time and convert to seconds
        long elapsedTime = System.nanoTime() - timeStart;
        time = elapsedTime / 1_000_000_000.0f; // Convert nanoseconds to seconds
    }
}

