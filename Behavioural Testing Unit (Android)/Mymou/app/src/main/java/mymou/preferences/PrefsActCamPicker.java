package mymou.preferences;

import android.content.SharedPreferences;
import android.hardware.camera2.CameraCharacteristics;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;

import android.util.Size;

import java.util.List;

import mymou.Utils.UtilsSystem;
import mymou.task.backend.CameraExternal;
import mymou.R;
import mymou.task.backend.CameraMain;

/**
 * The crop picker requires two separate fragments, one for the camera, and one for the crop overlay
 * This parent fragment simply loads up these two fragments
 */

public class PrefsActCamPicker extends FragmentActivity {

    private final String TAG = "PrefsActCamPicker";

    private PreferencesManager preferencesManager;
    private CameraMain cameraMain;
    private CameraExternal cameraExternal;
    private List<Size> resolutions;
    private int indexResolutionSelected;
    private String[] prefTags;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_picker);

        preferencesManager = new PreferencesManager(this);
        Log.d(TAG, "Camera to use: " + preferencesManager.camera_to_use);

        prefTags = new String[]{
                getString(R.string.preftag_camera_resolution_front),
                getString(R.string.preftag_camera_resolution_rear),
                getString(R.string.preftag_camera_resolution_ext)
        };

        createCameraSpinner();
        loadCamera();
    }

    private void createCameraSpinner() {
        final Spinner spinnerCameras = findViewById(R.id.spinner_cameras);

        final ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.cam_picker_camera_array, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        spinnerCameras.setAdapter(adapter);
        spinnerCameras.setSelection(preferencesManager.camera_to_use);
        spinnerCameras.setOnItemSelectedListener(cameraSpinnerListener);
    }

    private void loadCamera() {
        // Destroy existing cameras
        if (cameraMain != null) {
            cameraMain.onDestroy();
            cameraMain = null;
        } else if (cameraExternal != null) {
            cameraExternal.onDestroy();
            cameraExternal = null;
        }

        // Load camera fragment
        Bundle bundle = new Bundle();
        FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
        if (preferencesManager.camera_to_use != getApplicationContext().getResources().getInteger(R.integer.TAG_CAMERA_EXTERNAL)) {
            cameraMain = new CameraMain(null);
            cameraMain.setArguments(bundle);
            cameraMain.setFragInterfaceListener(this::loadSpinnerResolutionOptions);
            fragmentTransaction.add(R.id.layout_camerapicker, cameraMain, "camera_fragment");
        } else {
            cameraExternal = new CameraExternal();
            cameraExternal.setArguments(bundle);
            cameraExternal.setFragInterfaceListener(this::loadSpinnerResolutionOptions);
            fragmentTransaction.add(R.id.layout_camerapicker, cameraExternal, "camera_fragment");
        }
        fragmentTransaction.commit();
    }

    private void loadSpinnerResolutionOptions() {
        // Figure out which resolutions to load
        switch (preferencesManager.camera_to_use) {
            case CameraCharacteristics.LENS_FACING_BACK:
            case CameraCharacteristics.LENS_FACING_FRONT:
                resolutions = cameraMain.resolutions;
                break;
            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                resolutions = cameraExternal.resolutions;
                break;
            default:
                Toast.makeText(getApplicationContext(),
                        "Error: Camera Was Not Loaded Properly", Toast.LENGTH_LONG).show();
                return;
        }

        // Convert resolutions list to array
        final CharSequence[] resolutionsChar = new CharSequence[resolutions.size()];
        Size size;
        for (int i = 0; i < resolutions.size(); i++) {
            size = resolutions.get(i);
            resolutionsChar[i] = size.getWidth() + "×" + size.getHeight();
        }

        // Create a new resolution spinner
        final Spinner spinnerResolutions = findViewById(R.id.spinner_resolutions);
        final ArrayAdapter<CharSequence> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, resolutionsChar);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerResolutions.setAdapter(adapter);

        // Get the saved resolution
        int default_size = UtilsSystem.getIndexMinResolution(resolutions);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        switch (preferencesManager.camera_to_use) {
            case CameraCharacteristics.LENS_FACING_BACK:
                indexResolutionSelected = settings.getInt(getString(R.string.preftag_camera_resolution_rear), default_size);
                break;
            case CameraCharacteristics.LENS_FACING_FRONT:
                indexResolutionSelected = settings.getInt(getString(R.string.preftag_camera_resolution_front), default_size);
                break;
            case CameraCharacteristics.LENS_FACING_EXTERNAL:
                indexResolutionSelected = settings.getInt(getString(R.string.preftag_camera_resolution_ext), default_size);
                break;
            default:
                indexResolutionSelected = -1;
        }
        assert indexResolutionSelected != -1;

        // Set the resolution spinner to the saved resolution
        spinnerResolutions.setSelection(indexResolutionSelected);
        spinnerResolutions.setOnItemSelectedListener(resolutionSpinnerListener);
    }

    private final AdapterView.OnItemSelectedListener cameraSpinnerListener =
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    Log.d(TAG, "cameraSpinnerListener; onItemSelected: pos: " + pos);
                    String cameraName = (String) parent.getItemAtPosition(pos);
                    if (cameraName.equals(getString(R.string.cam_picker_camera_selfie))) {
                        switch_camera(getApplicationContext().getResources().getInteger(R.integer.TAG_CAMERA_FRONT));
                    } else if (cameraName.equals(getString(R.string.cam_picker_camera_rear))) {
                        switch_camera(getApplicationContext().getResources().getInteger(R.integer.TAG_CAMERA_REAR));
                    } else if (cameraName.equals(getString(R.string.cam_picker_camera_external))) {
                        switch_camera(getApplicationContext().getResources().getInteger(R.integer.TAG_CAMERA_EXTERNAL));
                    }
                }

                public void onNothingSelected(AdapterView<?> parent) {
                }
            };

    private void switch_camera(int id) {
        // Check they have actually changed the choice
        if (id == preferencesManager.camera_to_use) return;

        // Save new choice
        Log.d(TAG, "Saving Camera: " + preferencesManager.camera_to_use);
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();
        editor.putInt(getString(R.string.preftag_camera_to_use), id);
        editor.apply();

        preferencesManager.camera_to_use = id;
        loadCamera();
    }

    private final AdapterView.OnItemSelectedListener resolutionSpinnerListener =
            new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                    Log.d(TAG, "resolutionSpinnerListener; pos: " + pos +
                            ", current: " + indexResolutionSelected);

                    if (pos == indexResolutionSelected) return;

                    // Store the value
                    final SharedPreferences settings = PreferenceManager
                            .getDefaultSharedPreferences(getApplicationContext());
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putInt(prefTags[preferencesManager.camera_to_use], pos);

                    // For external camera we have to store the width and height separately so it can be set on startup
                    if (preferencesManager.camera_to_use == getApplicationContext().getResources().getInteger(R.integer.TAG_CAMERA_EXTERNAL)) {
                        final Size cameraResolution = resolutions.get(pos);
                        editor.putInt(getApplicationContext().getResources().getString(R.string.preftag_camera_resolution_ext_width), cameraResolution.getWidth());
                        editor.putInt(getApplicationContext().getResources().getString(R.string.preftag_camera_resolution_ext_height), cameraResolution.getHeight());
                    }

                    // Commit now as loadCamera() will create a new Camera which pulls the setting we're saving
                    editor.commit();
                    loadCamera();
                }

                public void onNothingSelected(AdapterView<?> parent) {
                }
            };
}
