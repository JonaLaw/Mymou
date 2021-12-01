package mymou.preferences;

import android.os.Bundle;
import android.util.Log;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragMenu extends PreferenceFragmentCompat {

    private String TAG = "MymouPrefsFragSystem";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_menu, rootKey);
        Log.d(TAG, "onCreatePreferences");
    }
}
