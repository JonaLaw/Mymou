package mymou.preferences;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragTaskTrainingOne extends PreferenceFragmentCompat  {

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_training_one, rootKey);
    }
}
