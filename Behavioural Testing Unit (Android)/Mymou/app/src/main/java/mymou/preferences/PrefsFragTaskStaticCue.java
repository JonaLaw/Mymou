package mymou.preferences;


import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Point;
import android.os.Bundle;
import android.text.InputType;
import android.view.Display;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import mymou.R;

public class PrefsFragTaskStaticCue extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Resources r;

    public PrefsFragTaskStaticCue() {
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_trainingstaticcue, rootKey);
        r = getContext().getResources();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Get size of screen
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        // Set x options based on screen width
        SeekBarPreferenceCustom seekBar = (SeekBarPreferenceCustom) findPreference(getString(R.string.preftag_t_sc_cuex));
        seekBar.setMax(size.x);
        int xval = sharedPrefs.getInt(getString(R.string.preftag_t_sc_cuex), size.x / 2);
        seekBar.setValue(xval);

        SeekBarPreferenceCustom seekBar3 = (SeekBarPreferenceCustom) findPreference(getString(R.string.preftag_t_sc_cuextwo));
        seekBar3.setMax(size.x);
        int xval2 = sharedPrefs.getInt(getString(R.string.preftag_t_sc_cuextwo), size.x / 2);
        seekBar3.setValue(xval2);

        // Set y options based on screen height
        SeekBarPreferenceCustom seekBar2 = (SeekBarPreferenceCustom) findPreference(getString(R.string.preftag_t_sc_cuey));
        seekBar2.setMax(size.y);
        int yval = sharedPrefs.getInt(getString(R.string.preftag_t_sc_cuey), size.y / 2);
        seekBar2.setValue(yval);

        SeekBarPreferenceCustom seekBar4 = (SeekBarPreferenceCustom) findPreference(getString(R.string.preftag_t_sc_cueytwo));
        seekBar4.setMax(size.y);
        int yval2 = sharedPrefs.getInt(getString(R.string.preftag_t_sc_cueytwo), size.y / 2);
        seekBar4.setValue(yval2);

        // Conditional settings
        if (sharedPrefs.getBoolean(getContext().getResources().getString(R.string.preftag_t_sc_stopsess), r.getBoolean(R.bool.default_t_sc_stopsess))) {
            findPreference(getContext().getResources().getString(R.string.preftag_t_sc_sess_length)).setVisible(true);
        }
        if (sharedPrefs.getBoolean(getContext().getResources().getString(R.string.preftag_t_sc_alternatecue), r.getBoolean(R.bool.default_t_sc_alternatecue))) {
            findPreference(getContext().getResources().getString(R.string.preftag_t_sc_cuextwo)).setVisible(true);
            findPreference(getContext().getResources().getString(R.string.preftag_t_sc_cueytwo)).setVisible(true);
        }

        // Set onchange listener
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {

        if (key.equals(getContext().getResources().getString(R.string.preftag_t_sc_stopsess))) {
            findPreference(getContext().getResources().getString(R.string.preftag_t_sc_sess_length)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_t_sc_stopsess)));
        }
        if (key.equals(getContext().getResources().getString(R.string.preftag_t_sc_alternatecue))) {
            findPreference(getContext().getResources().getString(R.string.preftag_t_sc_cuextwo)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_t_sc_alternatecue)));
            findPreference(getContext().getResources().getString(R.string.preftag_t_sc_cueytwo)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_t_sc_alternatecue)));
        }

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

}
