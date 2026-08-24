package com.cfks.goosedroid;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.provider.*;

/**
 * Helper class for managing Android permissions.
 */
public class PermissionRequest {
    private Activity activity;
    private PermissionHelper helper;

    public PermissionRequest(Activity context) {
        this.activity = context;
        helper = new PermissionHelper(context);
    }

    /**
     * Get the current app version code.
     */
    public int getAppVersionCode() {
        PackageManager manager = activity.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(activity.getPackageName(), 0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 0;
    }

    /**
     * Check if the app has overlay permission.
     */
    public boolean hasOverlayPermission() {
        return Settings.canDrawOverlays(activity);
    }

    /**
     * Check if the app has install packages permission.
     */
    public boolean hasInstallPermission() {
        return activity.getPackageManager().canRequestPackageInstalls();
    }

    /**
     * Request all permissions declared in the manifest.
     */
    public void requestAllPermissions(PermissionHelper.PermissionResultCallback callback) {
        helper.requestPermission(activity, callback, getAllAppPermissions());
    }

    /**
     * Request specific permissions.
     */
    public void requestPermissions(PermissionHelper.PermissionResultCallback callback, String... permissions) {
        helper.requestPermission(activity, callback, permissions);
    }

    /**
     * Open system settings to request overlay permission.
     * Request code: 5003
     */
    public void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
        intent.setData(Uri.parse("package:" + activity.getPackageName()));
        activity.startActivityForResult(intent, 5003);
    }

    /**
     * Open system settings to request install packages permission.
     * Request code: 5004
     */
    public void requestInstallPermission() {
        Uri packageURI = Uri.parse("package:" + activity.getPackageName());
        Intent intent = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageURI);
        activity.startActivityForResult(intent, 5004);
    }

    /**
     * Check if any of the specified permissions are missing.
     */
    public boolean lacksPermissions(String... permissions) {
        return helper.lacksPermissions(permissions);
    }

    /**
     * Get all permissions declared in the app manifest.
     */
    public String[] getAllAppPermissions() {
        PackageManager manager = activity.getPackageManager();
        try {
            PackageInfo info = manager.getPackageInfo(activity.getPackageName(), 0);
            String pkgName = info.packageName;
            PackageInfo packageInfo = manager.getPackageInfo(pkgName, PackageManager.GET_PERMISSIONS);
            return packageInfo.requestedPermissions;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            return new String[0];
        }
    }
}
