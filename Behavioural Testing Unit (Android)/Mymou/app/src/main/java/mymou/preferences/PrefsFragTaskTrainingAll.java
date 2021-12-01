package mymou.preferences;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragTaskTrainingAll extends PreferenceFragmentCompat  {

    private final String trial_settings;

    public PrefsFragTaskTrainingAll() {
        trial_settings = null;
    }

    public PrefsFragTaskTrainingAll(String trial_settings) {
        this.trial_settings = trial_settings;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        if (trial_settings != null) {
            if (trial_settings.equals(getString(R.string.preftag_task_t_three_settings))) {
                setPreferencesFromResource(R.xml.preferences_task_training_three, rootKey);
            } else {
                setPreferencesFromResource(R.xml.preferences_task_training_all, rootKey);
                return;
            }
            addPreferencesFromResource(R.xml.preferences_task_training_all);
        } else {
            setPreferencesFromResource(R.xml.preferences_task_training_all, rootKey);
        }
    }
}
