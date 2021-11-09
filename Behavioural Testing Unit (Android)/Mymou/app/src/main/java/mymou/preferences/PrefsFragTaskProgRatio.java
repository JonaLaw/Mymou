package mymou.preferences;


import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.Display;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import mymou.R;

public class PrefsFragTaskProgRatio extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Resources r;

    public PrefsFragTaskProgRatio() {
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_prog_ratio, rootKey);
        r = getContext().getResources();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Get size of screen
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        // Set x options based on screen width
        SeekBarPreferenceCustom seekBar = (SeekBarPreferenceCustom) findPreference(getString(R.string.preftag_pr_cuex));
        seekBar.setMax(size.x);
        int xval = sharedPrefs.getInt(getString(R.string.preftag_pr_cuex), size.x/2);
        seekBar.setValue(xval);

        // Set y options based on screen height
        SeekBarPreferenceCustom seekBar2 = (SeekBarPreferenceCustom) findPreference(getString(R.string.preftag_pr_cuey));
        seekBar2.setMax(size.y);
        int yval = sharedPrefs.getInt(getString(R.string.preftag_pr_cuey), size.y/2);
        seekBar2.setValue(yval);

        // Conditional settings
        if (sharedPrefs.getBoolean(getContext().getResources().getString(R.string.preftag_pr_skip_go_cue), r.getBoolean(R.bool.default_pr_skip_go_cue))) {
             findPreference(getContext().getResources().getString(R.string.preftag_pr_timeout_length)).setVisible(true);
             findPreference(getContext().getResources().getString(R.string.preftag_pr_sess_length)).setVisible(true);
             findPreference(getContext().getResources().getString(R.string.preftag_pr_iti)).setVisible(true);
        }
        if (sharedPrefs.getBoolean(getContext().getResources().getString(R.string.preftag_pr_progress_bar), r.getBoolean(R.bool.default_pr_progress_bar))) {
             findPreference(getContext().getResources().getString(R.string.preftag_pr_animation_duration)).setVisible(true);
        }

        // Set onchange listener
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

    }

   @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if (key.equals(getContext().getResources().getString(R.string.preftag_pr_skip_go_cue))) {
             findPreference(getContext().getResources().getString(R.string.preftag_pr_timeout_length)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_pr_skip_go_cue)));
             findPreference(getContext().getResources().getString(R.string.preftag_pr_sess_length)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_pr_skip_go_cue)));
             findPreference(getContext().getResources().getString(R.string.preftag_pr_iti)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_pr_skip_go_cue)));
        }
              if (key.equals(getContext().getResources().getString(R.string.preftag_pr_progress_bar))) {
             findPreference(getContext().getResources().getString(R.string.preftag_pr_animation_duration)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_pr_progress_bar)));
        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

}
