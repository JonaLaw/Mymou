package mymou.Utils;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;

public class PermissionManager {

    private static final String TAG = "PermissionManager";
    private final Context context;
    private final Activity activity;

    // This does not include Manifest.permission.WRITE_SETTINGS as it is special
    private static final String[] permissionCodes = {
            Manifest.permission.CAMERA,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_COARSE_LOCATION
    };

    public PermissionManager(Context context, Activity activity) {
        this.context = context;
        this.activity = activity;
    }

    // Check if all permissions granted
    public boolean checkAllPermissionsGranted() {
        boolean allPermissionsGranted = true;
        for (String permission : permissionCodes) {
            if (!checkPermissionGranted(permission)) {
                allPermissionsGranted = false;
            }
        }
        if (allPermissionsGranted && !checkPermissionGranted(Manifest.permission.WRITE_SETTINGS)) {
            allPermissionsGranted = false;
        }
        Log.d(TAG, "checkAllPermissionsGranted: " + allPermissionsGranted);
        return allPermissionsGranted;
    }

    public void requestAllPermissions() {
        Log.d(TAG, "requestAllPermissions");
        activity.requestPermissions(permissionCodes, 123);
        if (!checkPermissionGranted(Manifest.permission.WRITE_SETTINGS)) {
            requestPermission(Manifest.permission.WRITE_SETTINGS);
        }
    }

    public boolean checkPermissionGranted(String permission) {
        Log.d(TAG, "checkPermissionGranted: " + permission);
        if (permission.equals(Manifest.permission.WRITE_SETTINGS)) {
            return Settings.System.canWrite(context);
        } else {
            return (activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED);
        }
    }

    private void requestPermission(String permission) {
        Log.d(TAG, "requestPermission: " + permission);
        // This one is handled differently
        if (permission.equals(Manifest.permission.WRITE_SETTINGS)) {
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } else {
            activity.requestPermissions(new String[]{permission}, 123);
        }
    }

    public void permissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult");
        if (grantResults.length > 0) {
            for (int i = 0; i < permissions.length; i++) {
                Log.d(TAG, "onRequestPermissionsResult permission: " + permissions[i] +
                        ", grantResults: " + grantResults[i]);
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    // Should we show an explanation?
                    if (ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions[i])) {
                        // Show permission explanation dialog...
                        displayPermissionDeniedAlertDialog(permissions[i]);
                    } else {
                        // Never ask again selected, or device policy prohibits the app from having that permission.
                        // So, disable that feature, or fall back to another situation...
                        displayPermissionGoneAlertDialog(permissions[i]);
                    }
                }
            }
        }
    }

    private void displayPermissionDeniedAlertDialog(String permission) {
        Log.d(TAG, "displayPermissionDeniedAlertDialog: " + permission);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage("The permission: \"" + permission + "\" was not granted. It is needed inorder for this app to function correctly.")
                .setTitle("Warning - Permission Not Granted")
                .setPositiveButton("Try Again", (dialog, id) -> {
                    Log.d(TAG, "onClick: requesting permission");
                    // Request Permission Again
                    requestPermission(permission);
                })
                .setNegativeButton("Dismiss", (dialog, id) -> {
                    Log.d(TAG, "onClick: dismissing permission");
                    //Do nothing
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void displayPermissionGoneAlertDialog(String permission) {
        Log.d(TAG, "displayPermissionGoneAlertDialog: " + permission);
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage("The permission: \"" + permission + "\" was not, or cannot, be granted.\n\n" +
                "Please navigate to your devices system settings and grant this app its required permissions.")
                .setTitle("Warning - Permission Hidden")
                .setPositiveButton("Open Settings", (dialog, id) -> {
                    Log.d(TAG, "onClick: opening permission settings");
                    // Open the app's permission page in the devices settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.fromParts("package", activity.getPackageName(), null));
                    activity.startActivity(intent);
                })
                .setNegativeButton("Dismiss", (dialog, id) -> {
                    Log.d(TAG, "onClick: dismissing permission settings");
                    //Do nothing
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }
}