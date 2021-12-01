package mymou.preferences;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragCamera extends PreferenceFragmentCompat {

    private final String TAG = "MymouPrefsFragCamera";

    public PrefsFragCamera() {
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_camera, rootKey);
        Log.d(TAG, "onCreatePreferences");
    }
}
