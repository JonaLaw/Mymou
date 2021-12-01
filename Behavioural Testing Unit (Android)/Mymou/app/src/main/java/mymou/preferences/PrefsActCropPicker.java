package mymou.preferences;

import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import mymou.task.backend.CameraMain;
import mymou.R;

/**
 * The crop picker requires two separate fragments, one for the camera, and one for the crop overlay
 * This parent fragment simply loads up these two fragments
 */

public class PrefsActCropPicker extends FragmentActivity {

    public static String TAG = "PrefsActCropPicker";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crop_picker);
        Log.d(TAG, "Loading activity");

        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();

        // Load camera fragment
        Bundle bundle = new Bundle();
        bundle.putBoolean(getResources().getString(R.string.crop_picker_mode), true);
        CameraMain cameraFragment = new CameraMain(null);
        cameraFragment.setArguments(bundle);

        // Load crop picker fragment
        PrefsFragCropPicker cropPickerFragment = new PrefsFragCropPicker(this, this);

        // Commit fragments
        fragmentTransaction.add(R.id.layout_croppicker, cameraFragment, "camera_fragment");
        fragmentTransaction.add(R.id.layout_croppicker, cropPickerFragment, "crop_fragment");
        fragmentTransaction.commit();
    }
}
