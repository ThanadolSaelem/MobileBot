package com.cfks.goosedroid;

import android.app.*;
import android.content.pm.*;
import androidx.annotation.*;
import androidx.core.app.*;
import androidx.core.content.*;


public class PermissionHelper
 {

    private Activity mActivity;
    private final int REQUEST_CODE = 1000;
    private PermissionResultCallback mPermissionResultCallback;

    public PermissionHelper(Activity activity) {
        mActivity = activity;
    }

    // Public interface to request permissions
    public void requestPermission(Activity activity, PermissionResultCallback permissionResultCallback, String... permissions) {
        ActivityCompat.requestPermissions(activity, permissions, REQUEST_CODE);
        this.mPermissionResultCallback = permissionResultCallback;
    }


    // Check if any permissions are missing from the collection
    public boolean lacksPermissions(String... permissions) {
        for (String permission : permissions) {
            if (lacksPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    // Check if a single permission is missing
    private boolean lacksPermission(String permission) {
        return ContextCompat.checkSelfPermission(mActivity, permission) ==
                PackageManager.PERMISSION_DENIED;
    }


    // Check if all permissions were granted
    public boolean hasAllPermissionsGranted(@NonNull int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }


    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (mPermissionResultCallback != null) {
            mPermissionResultCallback.onPermissionResult(hasAllPermissionsGranted(grantResults));
        }
    }

    public interface PermissionResultCallback {
        void onPermissionResult(boolean allGranted);
    }
}

