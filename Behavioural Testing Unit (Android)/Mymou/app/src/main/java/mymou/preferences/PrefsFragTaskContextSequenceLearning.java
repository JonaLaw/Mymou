package mymou.preferences;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragTaskContextSequenceLearning extends PreferenceFragmentCompat  {

    private String TAG="MymouPrefsFragTaskContextSequenceLearning";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_contextsequencelearning, rootKey);
    }
}
