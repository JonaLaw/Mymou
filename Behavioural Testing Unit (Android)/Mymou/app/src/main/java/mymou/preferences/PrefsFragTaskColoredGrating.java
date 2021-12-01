package mymou.preferences;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragTaskColoredGrating extends PreferenceFragmentCompat  {

    private String TAG="MymouPrefsFragTaskColoredGrating";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_coloredgrating, rootKey);
    }
}
