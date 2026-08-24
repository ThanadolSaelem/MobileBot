/**
 * @Author
 * @AIDE AIDE+
 */
package com.cfks.goosedroid;

import android.content.*;
import android.util.Log;

import java.io.*;
import java.util.*;

public class ConfigureActivity {
    private static final String TAG = "ConfigureActivity";
    private final Context context;
    private Properties properties;

    public ConfigureActivity(Context context) {
        super();
        this.context = context;
        this.properties = new Properties();
    }

    /**
     * Save properties to file using try-with-resources for proper cleanup.
     * @param filename Path to the file
     * @param properties Properties to save
     * @throws IOException if file cannot be written
     */
    public void saveFiletoSD(String filename, Properties properties) throws IOException {
        // Validate inputs
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }
        if (properties == null) {
            throw new IllegalArgumentException("Properties cannot be null");
        }

        // Use try-with-resources to ensure stream is closed
        try (FileOutputStream fileOutputStream = new FileOutputStream(filename);
             BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream)) {

            // Get all keys and write them
            for (String key : properties.stringPropertyNames()) {
                String s = key + " = " + properties.getProperty(key) + "\n";
                bufferedOutputStream.write(s.getBytes());
            }

            // Ensure data is written
            bufferedOutputStream.flush();

        } catch (IOException e) {
            Log.e(TAG, "Error saving config file: " + filename, e);
            throw e; // Re-throw to let caller handle
        }
    }

    /**
     * Read properties from file using try-with-resources for proper cleanup.
     * @param filename Path to the file
     * @throws IOException if file cannot be read
     */
    public void readFromSD(String filename) throws IOException {
        // Validate input
        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        // Initialize properties
        properties = new Properties();

        // Validate file exists and is readable
        File file = new File(filename);
        if (!file.exists()) {
            throw new FileNotFoundException("Config file not found: " + filename);
        }
        if (!file.canRead()) {
            throw new IOException("Config file is not readable: " + filename);
        }

        // Validate file size (prevent loading huge files)
        long maxSize = 1024 * 1024; // 1MB max
        if (file.length() > maxSize) {
            throw new IOException("Config file too large: " + file.length() + " bytes (max: " + maxSize + ")");
        }

        // Use try-with-resources to ensure stream is closed
        try (FileInputStream fileInputStream = new FileInputStream(file);
             BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream)) {

            properties.load(bufferedInputStream);

        } catch (IOException e) {
            Log.e(TAG, "Error reading config file: " + filename, e);
            throw e; // Re-throw to let caller handle
        }
    }

    /**
     * Get a property value by key.
     * @param key The property key
     * @return The property value, or null if not found
     */
    public String getIniKey(String key) {
        if (properties == null) {
            Log.w(TAG, "Properties not loaded - call readFromSD() first");
            return null;
        }
        if (key == null) {
            return null;
        }
        if (!properties.containsKey(key)) {
            return null;
        }
        return String.valueOf(properties.get(key));
    }

    /**
     * Check if a property exists.
     * @param key The property key
     * @return true if the property exists
     */
    public boolean hasKey(String key) {
        return properties != null && key != null && properties.containsKey(key);
    }

    /**
     * Get property with default value if not found.
     * @param key The property key
     * @param defaultValue Value to return if key not found
     * @return The property value or default
     */
    public String getIniKey(String key, String defaultValue) {
        String value = getIniKey(key);
        return value != null ? value : defaultValue;
    }

    /**
     * Set a property value.
     * @param key The property key
     * @param value The property value
     */
    public void setIniKey(String key, String value) {
        if (properties == null) {
            properties = new Properties();
        }
        if (key != null && value != null) {
            properties.setProperty(key, value);
        }
    }

    /**
     * Get the properties object.
     * @return The properties
     */
    public Properties getProperties() {
        return properties;
    }
}
