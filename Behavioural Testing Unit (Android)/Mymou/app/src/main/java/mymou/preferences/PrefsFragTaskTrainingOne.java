package mymou.preferences;

import android.os.Bundle;

import androidx.preference.PreferenceFragmentCompat;

import mymou.R;

public class PrefsFragTaskTrainingOne extends PreferenceFragmentCompat  {

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_training_one, rootKey);

        SeekBarPreferenceCustom seekBar5 = (SeekBarPreferenceCustom) findPreference(getString(R.string.preftag_t_one_num_presses));
        seekBar5.setMin(1);
        seekBar5.setMax(10);
    }
}
