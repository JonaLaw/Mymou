package mymou.preferences;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragTaskWald extends PreferenceFragmentCompat  {

    private String TAG="MymouPrefsFragTaskWald";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_wald, rootKey);
    }
}
