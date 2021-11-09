package mymou.preferences;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputType;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import mymou.R;
import mymou.Utils.PlayCustomTone;

public class PrefsFragTaskContextSequenceLearning extends PreferenceFragmentCompat  {

    private String TAG="MymouPrefsFragTaskContextSequenceLearning";

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        setPreferencesFromResource(R.xml.preferences_task_contextsequencelearning, rootKey);
    }

}
