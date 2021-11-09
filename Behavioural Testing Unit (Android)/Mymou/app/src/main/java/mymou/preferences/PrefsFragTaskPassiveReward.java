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

public class PrefsFragTaskPassiveReward extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    private Resources r;

    public PrefsFragTaskPassiveReward() {
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_passivereward, rootKey);
        r = getContext().getResources();

        SharedPreferences sharedPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());

        // Conditional settings
        if (sharedPrefs.getBoolean(getContext().getResources().getString(R.string.preftag_pass_stopsess), r.getBoolean(R.bool.default_pass_stopsess))) {
             findPreference(getContext().getResources().getString(R.string.preftag_pass_sess_length)).setVisible(true);
        }

        // Set onchange listener
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

    }

   @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(getContext().getResources().getString(R.string.preftag_pass_stopsess))) {
             findPreference(getContext().getResources().getString(R.string.preftag_pass_sess_length)).setVisible(sharedPreferences.getBoolean(key, r.getBoolean(R.bool.default_pass_stopsess)));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

}
