package mymou.preferences;

import android.content.DialogInterface;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;
import mymou.R;

/**
 * Common preference fragment used to manage all preferences that have no special behaviour
 */

public class PrefsFragTaskAll extends PreferenceFragmentCompat {

    private final String TAG = "MymouPrefsFragTaskAll";

    public PrefsFragTaskAll() {
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        Log.d(TAG, "Preference loaded");
        setPreferencesFromResource(R.xml.preferences_task_all, rootKey);
    }


}
