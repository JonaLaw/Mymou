package mymou;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.*;

import androidx.preference.PreferenceManager;

import mymou.Utils.FilenameValidation;
import mymou.Utils.PermissionManager;
import mymou.Utils.UtilsSystem;
import mymou.Utils.FolderManager;
import mymou.preferences.PreferencesManager;
import mymou.task.backend.DataViewer;
import mymou.task.backend.RewardSystem;
import mymou.task.backend.TaskManager;
import mymou.task.backend.UtilsTask;
import mymou.preferences.PrefsActSystem;

public class MainMenu extends Activity {

    private static String TAG = "MyMouMainMenu";

    private static PreferencesManager preferencesManager;
    private PermissionManager permissionManager;
    private static RewardSystem rewardSystem;
    private static FolderManager folderManager;

    // Default channel to be activated by the pump
    private static int reward_chan;

    // The task to be loaded, set by the spinner
    private static int taskSelected = 2;

    private Context context = this;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // Retrieve settings
        preferencesManager = new PreferencesManager(this);
        permissionManager = new PermissionManager(this, this);
        // Get Current Folder Status
        folderManager = new FolderManager(this, 0);

        initialiseLayoutParameters();

        checkIfCrashed();

        initialiseSpinner();

        initialiseFileNameSettings();

        UtilsSystem.setBrightness(true, this, preferencesManager);

    }

    private boolean checkPermissions() {
        Log.d(TAG, "checkPermissions: checking permissions");
        if (!permissionManager.checkAllPermissionsGranted()) {
            Log.d(TAG, "checkPermissions: permissions not granted");
            displayPermissionAlertDialog();
            return false;
        }
        return true;
    }

    private void displayPermissionAlertDialog() {
        Log.d(TAG, "displayPermissionAlertDialog: displaying permission AD");
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("This app requires various permissions to perform tasks properly." +
                "\n\nNot all required permissions are currently granted, please grant them to continue.")
                .setTitle("Requesting Permissions")
                .setPositiveButton("Grant Permissions", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Request Permissions
                        Log.d(TAG, "onClick: requesting permissions");
                        permissionManager.requestAllPermissions();
                    }
                })
                .setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        //Do nothing
                        Log.d(TAG, "onClick: dismissing permissions");
                    }
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void startTask() {
        Log.d(TAG, "startTask: trying to start task");
        
        // Task can only start if all permissions granted
        if (!checkPermissions()) return;

        Button startButton = findViewById(R.id.buttonStart);
        startButton.setText("Loading...");

        Log.d(TAG, "Starting TaskManager as Intent...");

        rewardSystem.quitBt();  // Reconnect from next activity

        Intent intent = new Intent(this, TaskManager.class);
        intent.putExtra("tasktoload", taskSelected);

        startActivity(intent);
    }

    private void initialiseRewardSystem() {
        // Initialise reward system
        rewardSystem = new RewardSystem(this, this);
        updateRewardText();

        // Set object listener to react when bluetooth status changes
        rewardSystem.setCustomObjectListener(new RewardSystem.MyCustomObjectListener() {
            @Override
            public void onChangeListener() {
                updateRewardText();
            }
        });

        // And now we can try to connect
        rewardSystem.connectToBluetooth();

    }

    private void updateRewardText() {
        Log.d(TAG, "Updating reward controller " + rewardSystem.status);
        TextView tv1 = findViewById(R.id.tvBluetooth);
        tv1.setText(rewardSystem.status);
        Button connectToBt = findViewById(R.id.buttConnectToBt);
        if (rewardSystem.status.equals("Connection failed")) {
            UtilsTask.toggleCue(connectToBt, true);
            connectToBt.setText("Connect");
        } else {
            UtilsTask.toggleCue(connectToBt, false);
        }
    }

    // This is the dropdown menu to select task to load
    private void initialiseSpinner() {
        Spinner spinner = (Spinner) findViewById(R.id.spinnerTaskMenu);
        // Create an ArrayAdapter using the string array and a default spinner layout
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.available_tasks, android.R.layout.simple_spinner_item);
        // Specify the layout to use when the list of choices appears
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        // Apply the adapter to the spinner
        spinner.setAdapter(adapter);

        // Find previously selected task and set spinner to this position
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String key = "task_selected";
        taskSelected = settings.getInt(key, 0);

        // Set up UI for currently selected task
        spinner.setSelection(taskSelected);

        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> adapter, View v, int position, long id) {

                // Update task selected
                taskSelected = position;

                // Store for future reference
                SharedPreferences.Editor editor = settings.edit();
                editor.putInt(key, position);
                editor.commit();
            }

            @Override
            public void onNothingSelected(AdapterView<?> arg0) {
            }
        });

    }

    private void checkIfCrashed() {
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            if (extras.getBoolean("restart")) {
                //If crashed then restart task
                Log.d(TAG, "checkIfCrashed() restarted task");
                startTask();
            }
        }
    }

    private void initialiseLayoutParameters() {
        // Buttons
        findViewById(R.id.buttonStart).setOnClickListener(buttonClickListener);
        findViewById(R.id.buttonSettings).setOnClickListener(buttonClickListener);
        findViewById(R.id.buttonTaskSettings).setOnClickListener(buttonClickListener);
        findViewById(R.id.buttonViewData).setOnClickListener(buttonClickListener);
        findViewById(R.id.info_button).setOnClickListener(buttonClickListener);
        findViewById(R.id.buttConnectToBt).setOnClickListener(buttonClickListener);
        findViewById(R.id.toggleButtonFilenameByDate).setOnClickListener(buttonClickListener);
        findViewById(R.id.buttonSaveFilename).setOnClickListener(buttonClickListener);

        // Disabled as in development
//        findViewById(R.id.buttonViewData).setEnabled(false);

        // Radio groups (reward system controller)
        reward_chan = preferencesManager.default_rew_chan;
        RadioButton[] radioButtons = new RadioButton[preferencesManager.max_reward_channels];
        radioButtons[0] = findViewById(R.id.rb_chan0);
        radioButtons[1] = findViewById(R.id.rb_chan1);
        radioButtons[2] = findViewById(R.id.rb_chan2);
        radioButtons[3] = findViewById(R.id.rb_chan3);
        radioButtons[reward_chan].setChecked(true);

        for (int i = 0; i < preferencesManager.max_reward_channels; i++) {
            boolean active = i >= preferencesManager.num_reward_chans ? false : true;
            UtilsTask.toggleView(radioButtons[i], active);
        }

        RadioGroup group = findViewById(R.id.rg_rewchanpicker);
        group.setOnCheckedChangeListener(checkedChangeListener);
        RadioGroup group2 = findViewById(R.id.rg_rewonoff);
        group2.setOnCheckedChangeListener(checkedChangeListener);

        // Reset text on start button in case they are returning from task
        Button startButton = findViewById(R.id.buttonStart);
        startButton.setText("Start Task");
    }

    private void initialiseFileNameSettings() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        // Set the filename as data TB to what was saved
        ToggleButton toggleButtonFilenameByDate = findViewById(R.id.toggleButtonFilenameByDate);
        boolean filenameByDate = settings.getBoolean(getString(R.string.filename_by_date_key), true);
        toggleButtonFilenameByDate.setChecked(filenameByDate);
        Log.d(TAG, "initialiseFileNameSettings: filenameByDate=" + filenameByDate);

        // This allows for filenames that go offscreen to marquee scroll
        TextView textViewSavingTo = findViewById(R.id.textViewSavingTo);
        textViewSavingTo.setSelected(true);

        // Updated the rest of the filename info and start task button
        updateFilenameSettings(filenameByDate, false);
    }

    private void updateFilenameSettings(boolean fileNameByDate, boolean saveToSharedPreferences) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        LinearLayout linearLayoutFilenameEdit = findViewById(R.id.linearLayoutFilenameEdit);
        EditText editTextFileName = findViewById(R.id.editTextFileName);
        TextView textViewSavingTo = findViewById(R.id.textViewSavingTo);

        // If the togglebutton for date as filename is checked
        if (fileNameByDate) {
            Log.d(TAG, "updateFilenameSettings: setting filename=default");

            // Hide the edit text view and disable editing it
            linearLayoutFilenameEdit.setVisibility(View.GONE);
            editTextFileName.setEnabled(false);
            // Set the displayed saved filename to the current date
            textViewSavingTo.setText(String.format("%s.txt", folderManager.getBaseDate()));
        }
        else {
            // Show the edit text view
            linearLayoutFilenameEdit.setVisibility(View.VISIBLE);

            String filename = settings.getString(getString(R.string.filename_custom_key),
                    getString(R.string.filename_default_string));
            // Check if the saved filename is not valid for some strange reason
            if (FilenameValidation.validateStringFilenameUsingContains(filename)) {

                Log.d(TAG, "updateFilenameSettings: setting filename=" + filename);
                // Set the filename to the saved filename and enabled editing it
                editTextFileName.setText(filename);
                editTextFileName.setEnabled(true);
                // Set the displayed saved filename to the new saved filename
                textViewSavingTo.setText(filename);
            }
            else {
                // Set the editText field to some default text, save it, and inform the user.
                Log.d(TAG, "updateFilenameSettings: invalid saved filename=" + filename);
                Toast.makeText(context, "The previously saved filename was invalid so it has been cleared.", Toast.LENGTH_LONG).show();
                editTextFileName.setText("default");
                saveFilename();
            }
        }

        // If this is an update directly from the user
        if (saveToSharedPreferences) {
            Log.d(TAG, "updateFilenameSettings: saving filename date setting");

            // Save the setting
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean(getString(R.string.filename_by_date_key), fileNameByDate);
            editor.apply();
        }
    }

    private void saveFilename() {
        // Get the filename and append ".txt" to it if necessary
        EditText editTextFileName = findViewById(R.id.editTextFileName);
        String filename = editTextFileName.getText().toString();
        if (!filename.endsWith(".txt")) {
            filename += ".txt";
            editTextFileName.setText(filename);
        }

        // Check if the filename is valid
        if (FilenameValidation.validateStringFilenameUsingContains(filename)) {
            // Updating the displayed saved filename that will be used
            TextView textViewSavingTo = findViewById(R.id.textViewSavingTo);
            textViewSavingTo.setText(filename);

            // Saving the filename
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor editor = settings.edit();
            editor.putString(getString(R.string.filename_custom_key), filename);
            editor.commit();
        }
        else {
            // Inform the user that the invalid filename was not saved
            Toast.makeText(context, "Invalid Filename Not Saved", Toast.LENGTH_SHORT).show();
        }
    }

    private RadioGroup.OnCheckedChangeListener checkedChangeListener =
            new RadioGroup.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(RadioGroup group, int checkedId) {
            int id = group.getCheckedRadioButtonId();
            switch (id) {
                case R.id.rb_chan0:
                    reward_chan = 0;
                    break;
                case R.id.rb_chan1:
                    reward_chan = 1;
                    break;
                case R.id.rb_chan2:
                    reward_chan = 2;
                    break;
                case R.id.rb_chan3:
                    reward_chan = 3;
                    break;
                case R.id.rb_pumpon:
                    if (!rewardSystem.bluetoothConnection) {
                        Log.d(TAG, "Error: Bluetooth not connected");
                        Toast.makeText(MainMenu.this, "Error: Bluetooth not connected/enabled", Toast.LENGTH_LONG).show();
                        RadioButton radioButton = findViewById(R.id.rb_pumpoff);
                        radioButton.setChecked(true);
                        return;
                    } else {
                        rewardSystem.startChannel(reward_chan);
                    }
                    break;
                case R.id.rb_pumpoff:
                    rewardSystem.stopChannel(reward_chan);
                    break;
            }

            // And always update default reward channel in case they changed value
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(getString(R.string.preftag_default_rew_chan), reward_chan).commit();
        }
    };

    private View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "onClick: " + view.getId());
            switch (view.getId()) {
                case R.id.buttonStart:
                    startTask();
                    break;
                case R.id.buttonSettings:
                    Intent intent = new Intent(context, PrefsActSystem.class);
                    intent.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_menu_prefs));
                    startActivity(intent);
                    break;
                case R.id.buttonTaskSettings:
                    Intent intent2 = new Intent(context, PrefsActSystem.class);
                    // Load task specific settings
                    boolean validsettings = true;
                    switch (taskSelected) {
                        case 0:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_pass_settings));
                            break;
                        case 1:
                        case 2:
                        case 3:
                        case 5:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_t_one_settings));
                            break;
                        case 4:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_t_sc_settings));
                            break;
                        case 8:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_disc_maze_settings));
                            break;
                        case 9:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_odc_settings));
                            break;
                        case 10:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_od_settings));
                            break;
                        case 11:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_pr_settings));
                            break;
                        case 12:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_ea_settings));
                            break;
                        case 13:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_sr_settings));
                            break;
                        case 14:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_sl_settings));
                            break;
                        case 15:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_rdm_settings));
                            break;
                        case 16:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_dvs_settings));
                            break;
                        case 17:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_csl_settings));
                            break;
                        case 18:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_wald_settings));
                            break;
                        case 19:
                            intent2.putExtra(getString(R.string.preftag_settings_to_load), getString(R.string.preftag_task_colgrat_settings));
                            break;
                        default:
                            validsettings = false;
                            Toast.makeText(getApplicationContext(), "Sorry, this task has no configurable settings", Toast.LENGTH_LONG).show();
                    }
                    if (validsettings) startActivity(intent2);
                    break;
                case R.id.buttonViewData:
                    Intent intent3 = new Intent(context, DataViewer.class);
                    startActivity(intent3);
                    break;
                case R.id.info_button:
                    AlertDialog.Builder builder = new AlertDialog.Builder(MainMenu.this);
                    String[] descriptions = getResources().getStringArray(R.array.task_descriptions);
                    String[] names = getResources().getStringArray(R.array.available_tasks);
                    builder.setMessage(descriptions[taskSelected])
                            .setTitle(names[taskSelected]);
                    AlertDialog dialog = builder.create();
                    dialog.show();
                    break;
                case R.id.buttConnectToBt:
                    if (rewardSystem.status.equals("Connection failed")) {
                        UtilsTask.toggleCue((Button) findViewById(R.id.buttConnectToBt), false);
                        rewardSystem.connectToBluetooth();
                    }
                    break;
                case R.id.toggleButtonFilenameByDate:
                    ToggleButton tg = (ToggleButton) view;
                    updateFilenameSettings(tg.isChecked(), true);
                    break;
                case R.id.buttonSaveFilename:
                    saveFilename();
                    break;
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume() called");
        preferencesManager = new PreferencesManager(this);
        initialiseLayoutParameters();
        initialiseRewardSystem();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "onPause() called");
        // Quit bluetooth
        if ( permissionManager.checkPermissionGranted(Manifest.permission.BLUETOOTH) &&
                permissionManager.checkPermissionGranted(Manifest.permission.BLUETOOTH_ADMIN)) {
            final Runnable r = new Runnable() {
                public void run() {
                    rewardSystem.quitBt();
                }
            };
            r.run();
        }
    }

    // TODO: Figure out how to move this to PermissionsManager
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: " + grantResults.length);
        permissionManager.permissionsResult(requestCode, permissions, grantResults);
    }
}
