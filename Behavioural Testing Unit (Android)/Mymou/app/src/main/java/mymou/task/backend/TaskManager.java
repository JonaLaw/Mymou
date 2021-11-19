package mymou.task.backend;

import android.Manifest;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.*;
import android.graphics.Point;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.Settings;
import android.util.Log;
import android.view.*;
import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.preference.PreferenceManager;
import androidx.room.Room;

import mymou.*;
import mymou.Utils.*;
import mymou.database.MymouDatabase;
import mymou.database.Session;
import mymou.preferences.PreferencesManager;
import mymou.preferences.PrefsActSystem;
import mymou.task.individual_tasks.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

public class TaskManager extends FragmentActivity implements View.OnClickListener {

    // Debug
    private final String TAG = "MyMouTaskManager";
    private TextView tvExplanation, tvErrors;  // Explanatory messages for demo mode, and any errors present

    private int taskId;  // Unique string prefixed to all log entries
    private final String TAG_FRAGMENT_TASK = "taskfrag";
    private final String TAG_FRAGMENT_CAMERA = "camerafrag";

    // Settings
    private String filename;
    private RewardSystem rewardSystem;
    private int latestRewardChannel;  // Track which reward channel was used so that it can be reused.
    private int faceRecogPrediction;  // Number corresponds to ID of the predicted subject
    private int monkeyButtonPressed;  // Each monkey has their individual go cue, which this tracks
    private boolean faceRecogRunning;  // If true, TaskManager will not start a trial as it is waiting for the result of faceRecog to be returned
    private boolean handle_feedback;  // If true, taskmanager will deliver reward for correct trials and display timeouts for incorrect trials

    private PreferencesManager preferencesManager;
    private FolderManager folderManager;
    private FaceRecog faceRecog;
    private Handler logHandler;
    private HandlerThread logThread;
    private FragmentManager fragmentManager;
    private FragmentTransaction fragmentTransaction;
    private Camera camera;

    // Async handlers used to posting delayed task events
    private final Handler h0;  // Task trial_timer
    private final Handler h1;  // Prepare for new trial
    private final Handler h2;  // Timeout go cues
    private final Handler h3;  // Daily timer
    private final Handler h4;  // Screen dim timer

    // Trial
    private ArrayList<String> trialData;
    public String photoTimestamp;
    private int trialCounter = 0;

    // Predetermined locations where cues can appear on screen,
    // calculated by UtilsTask.calculateCueLocations()
    private Point[] possible_cue_locs;

    // Timeouts for wrong choices by subject
    private final int timeoutWrongGoCuePressed = 300;  // Timeout for not pressing their own Go cue

    // Timer to reset task if subject stops halfway through a trial
    private int time;  // Time from last press - used for idle timeout if it reaches maxTrialDuration
    private boolean timerRunning;  // Signals if trial_timer currently active

    // Task objects
    private Button[] cues_Go; // Go cues to start a trial
    private Button[] cues_Reward;  // Reward cues for the different reward options

    // Boolean to signal if task should be active or not (e.g. overnight it is set to true)
    public boolean task_enabled;

    // Boolean to signal whether a trial is currently active on screen
    private boolean trial_running;

    // Loggers to track session variables
    private int l_rewgiven, l_numcorr;

    public TaskManager() {
        faceRecogPrediction = -1;
        monkeyButtonPressed = -1;
        faceRecogRunning = false;
        handle_feedback = true;

        h0 = new Handler();  // Task trial_timer
        h1 = new Handler();  // Prepare for new trial
        h2 = new Handler();  // Timeout go cues
        h3 = new Handler();  // Daily timer
        h4 = new Handler();  // Screen dim timer

        time = 0;
        task_enabled = true;
        trial_running = false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_all_tasks);

        // In case of crashes
        initialiseAutoRestartHandler();

        // Put the app into Kiosk mode
        initialiseScreenSettings();

        // Create ui Elements
        assignObjects();

        // Load settings
        setupFolderAndFile();
        loadtask();

        // Write all settings to disk for users to be able to check later
        new WriteSettingsToDisk(preferencesManager, taskId).run();

        // Now adjust UI elements depending on the settings
        setupCues();
        setOnClickListeners();

        // Load back end functions
        loadCamera();
        initialiseLogHandler();
        dailyTimer(false);
        if (preferencesManager.facerecog) {
            // Load facerecog off the main thread as takes a while
            Thread t = new Thread(() -> {
                faceRecog = new FaceRecog(this);
                if (!faceRecog.instantiated_successfully) {
                    tvErrors.setText(faceRecog.error_message);
                }
            });
            t.start();
        }

        // Disable app for now
        disableAllCues();
        enableApp(false);

        // Only lock if we aren't in testing mode
        tryTaskLocking();

        // Normally the reward system handles this as it has to wait for bluetooth connection
        if (!preferencesManager.bluetooth) {
            tvErrors.setText(getResources()
                    .getStringArray(R.array.error_messages)[getResources().getInteger(R.integer.i_bt_disabled)]);
        } else if (!rewardSystem.bluetoothConnection) {
            tvErrors.setText(getResources()
                    .getStringArray(R.array.error_messages)[getResources().getInteger(R.integer.i_bt_couldnt_connect)]);
        }

        // Lastly, we connect to the reward system,
        // which will then activate the task once it successfully connects to bluetooth
        initialiseRewardSystem();
    }

    private void initialiseAutoRestartHandler() {
        Log.d(TAG, "initialiseAutoRestartHandler");
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.d(TAG, "Task crashed");
            new CrashReport(throwable, TaskManager.this);
            if (!preferencesManager.debug) {
                rewardSystem.quitBt();
                restartApp();
            }
        });
    }

    private void initialiseScreenSettings() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
    }

    private void assignObjects() {
        // Global variables
        preferencesManager = new PreferencesManager(this);
        possible_cue_locs = UtilsTask.getPossibleCueLocs(this);
        trialData = new ArrayList<>();
        fragmentManager = getSupportFragmentManager();
        fragmentTransaction = fragmentManager.beginTransaction();
        latestRewardChannel = preferencesManager.default_rew_chan;

        tvExplanation = findViewById(R.id.tvLog);
        tvErrors = findViewById(R.id.tvError);
        UtilsTask.toggleView(tvExplanation, preferencesManager.debug);

        // Colours
        findViewById(R.id.task_container).setBackgroundColor(preferencesManager.taskbackground);
    }

    private void setupFolderAndFile() {
        // Setting up folder structure for the day
        // TODO This probably assumes that only one session will happen per day so
        //  multiple multi-monkey tests won't work
        if (preferencesManager.facerecog) {
            folderManager = new FolderManager(this, preferencesManager.num_monkeys);
        } else {
            folderManager = new FolderManager(this, 0);
        }

        // Creating the file for this test and adding the header
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        if (settings.getBoolean(getString(R.string.filename_by_date_key), true)) {
            filename = "default";
        } else {
            String settingsFilename = settings.getString(getString(R.string.filename_custom_key),
                    getString(R.string.filename_default_string));
            if (FilenameValidation.validateStringFilenameUsingContains(settingsFilename)) {
                filename = settingsFilename;
            } else {
                filename = "default";
            }
        }
        folderManager.tryMakingFileForTaskTrial(filename);
    }

    private void loadtask() {
        taskId = getIntent().getIntExtra("tasktoload", -1);

        // Load settings for task
        switch (taskId) {
            case 0:
            case 1:
            case 2:
            case 3:
            case 4:
            case 5:
                preferencesManager.TrainingTasks();
                break;
            case 6:
                break;
            case 7:
                preferencesManager.TrainingFiveTwoStep();
                break;
            case 8:
                preferencesManager.DiscreteMaze();
                break;
            case 9:
                preferencesManager.ObjectDiscriminationCol();
                break;
            case 10:
                preferencesManager.ObjectDiscrim();
                break;
            case 11:
                preferencesManager.ProgressiveRatio();
                // Reset numpresses needed
                SharedPreferences.Editor editor =
                        PreferenceManager.getDefaultSharedPreferences(this).edit();
                editor.putBoolean(preferencesManager.r.getString(R.string.pr_successful_trial), false);
                editor.commit();
                break;
            case 12:
                preferencesManager.EvidenceAccum();
                break;
            case 13:
                preferencesManager.SpatialResponse();
                break;
            case 14:
                preferencesManager.SequentialLearning();
                break;
            case 15:
                preferencesManager.RandomDotMotion();
                break;
            case 16:
                preferencesManager.DiscreteValueSpace();
                break;
            case 17:
                preferencesManager.ContextSequenceLearning();
                break;
            case 18:
                preferencesManager.Walds();
                break;
            case 19:
                preferencesManager.ColoredGrating();
                break;
            default:
                Log.d(TAG, "No task specified");
                new Exception("No task specified");
        }

        handle_feedback = preferencesManager.handle_feedback;
    }

    private void setupCues() {
        cues_Go = new Button[preferencesManager.num_monkeys];

        Log.d(TAG, "setupCues: " + cues_Go.length);

        // Setup the cues
        for (int i = 0; i < cues_Go.length; i++) {
            cues_Go[i] = UtilsTask.addColorCue(i, preferencesManager.colours_gocues[i],
                    this, this, findViewById(R.id.task_container));
            cues_Go[i].setBackgroundColor(preferencesManager.colours_gocues[i]);
        }

        // Check the size of the go cues
        if (possible_cue_locs.length < preferencesManager.num_monkeys) {
            new Exception("Go cues too big, not enough room for number of monkeys specified." +
                    "\nPlease reduce the size of the go cues or the number of monkeys");
        }

        // Go cues are in static location to make it easier for monkeys to press their own cue
        // If there is only one monkey then put the start cue in the middle of screen
        if (preferencesManager.num_monkeys == 1) {
            cues_Go[0].setX(possible_cue_locs[possible_cue_locs.length / 2].x);
            cues_Go[0].setY(possible_cue_locs[possible_cue_locs.length / 2].y);
        } else {
            // If multiple monkeys then tile the space evenly
            int pos;
            int step = 1;

            // If there's enough room then space the go cues around the screen
            if (possible_cue_locs.length > 2 * preferencesManager.num_monkeys) step *= 2;

            // Space them out
            for (int i = 0; i < cues_Go.length; i++) {
                if (i % 2 == 0) {
                    pos = i * step;
                } else {
                    pos = possible_cue_locs.length - (i * step);
                }
                cues_Go[i].setX(possible_cue_locs[pos].x);
                cues_Go[i].setY(possible_cue_locs[pos].y);
            }
        }

        // Reward cues for the different reward options
        cues_Reward = new Button[4];
        cues_Reward[0] = findViewById(R.id.buttonRewardZero);
        cues_Reward[1] = findViewById(R.id.buttonRewardOne);
        cues_Reward[2] = findViewById(R.id.buttonRewardTwo);
        cues_Reward[3] = findViewById(R.id.buttonRewardThree);

        // Disable go cues for extra monkeys
        UtilsTask.toggleCues(Arrays.copyOfRange(cues_Reward, preferencesManager.num_reward_chans, cues_Reward.length),
                false);

        // Shorten list to number needed
        cues_Reward = Arrays.copyOf(cues_Reward, preferencesManager.num_reward_chans);
    }

    private void setOnClickListeners() {
        findViewById(R.id.foregroundblack).setOnClickListener(this);
        UtilsSystem.setOnClickListenerLoop(cues_Reward, this);
        UtilsSystem.setOnClickListenerLoop(cues_Go, this);
    }

    private void loadCamera() {
        if (!preferencesManager.camera) return;

        Log.d(TAG, "Loading camera fragment");
        if (preferencesManager.camera_to_use != getResources().getInteger(R.integer.TAG_CAMERA_EXTERNAL)) {
            camera = new CameraMain(this);
        } else {
            camera = new CameraExternal(this);
        }

        camera.setFragInterfaceListener(() -> {
            Log.d(TAG, "Camera loaded");  // do nothing
        });

        Bundle bundle = new Bundle();
        bundle.putBoolean(getResources().getString(R.string.task_mode), true);
        camera.setArguments(bundle);
        fragmentTransaction.add(R.id.task_container, camera);
        commitFragment();
    }

    private void initialiseLogHandler() {
        logThread = new HandlerThread("LogBackground");
        logThread.start();
        logHandler = new Handler(logThread.getLooper());
    }

    // Recursive function to track time and switch app off when it hits a certain time
    public void dailyTimer(boolean shutdown) {
        Log.d(TAG, "dailyTimer called");
        final Calendar c = Calendar.getInstance();
        int hour = c.get(Calendar.HOUR);
        int min = c.get(Calendar.MINUTE);
        int AMPM = c.get(Calendar.AM_PM);
        if (AMPM == Calendar.PM) {
            hour += 12;
        }

        if (shutdown) {  // If shutdown and waiting to start up in the morning
            if (hour >= preferencesManager.autostart_hour &&
                    min > preferencesManager.autostart_min) {
                Log.d(TAG, "dailyTimer enabling app");

                if (preferencesManager.autostart) {
                    // Awaken screen
                    UtilsSystem.setBrightness(true, this, preferencesManager);
                    // Reactivate task
                    enableApp(true);
                }

                // Flip the switch so that timer is now waiting to shut down task
                shutdown = false;
            }
        } else {  // If active and waiting to shutdown
            if (hour >= preferencesManager.autostop_hour &&
                    min > preferencesManager.autostop_min) {
                Log.d(TAG, "dailyTimer disabling app");

                if (preferencesManager.autostop) {
                    // Dim screen
                    ContentResolver cResolver = this.getContentResolver();
                    Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, 0);
                    // Deactivate task
                    enableApp(false);
                }
                // Flip the switch so that timer is now waiting to shut down task
                shutdown = true;
            }
        }

        final boolean shutdown_f = shutdown;
        h3.postDelayed(() -> dailyTimer(shutdown_f), 60000);
    }

    private void disableAllCues() {
        UtilsTask.toggleCues(cues_Reward, false);
        UtilsTask.toggleCues(cues_Go, false);
    }

    public boolean enableApp(boolean bool) {
        Log.d(TAG, "Enabling app" + bool);

        View foregroundBlack = findViewById(R.id.foregroundblack);
        if (foregroundBlack != null) {
            task_enabled = bool;
            foregroundBlack.bringToFront();
            findViewById(R.id.tvError).bringToFront();
            // This is inverted as foreground object disables app
            UtilsTask.toggleView(foregroundBlack, !bool);

            if (bool) {
                PrepareForNewTrial(0);
            } else {
                killTask();
            }

            return true;
        } else {
            Log.d(TAG, "foregroundBlack object not instantiated");
            return false;
        }
    }

    private void tryTaskLocking() {
        // Check if debug mode is enabled in the app's System Settings
        if (PreferencesManager.debug) {
            displayDebugDialog();
        } else {
            // Check if permission is granted to pin the screen
            // This should almost never happen as it's checked right before a task is started
            if (new PermissionManager(this, this)
                    .checkPermissionGranted(Manifest.permission.WRITE_SETTINGS)) {
                this.startLockTask();
            } else {
                displayLockingErrorDialog();
            }
        }
    }

    private void displayDebugDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(TaskManager.this);
        builder.setTitle("Warning - App in Debug mode")
                .setMessage("The back key is currently functioning, " +
                        "and can be used to exit the task." +
                        "\nThis is not recommended for actual training." +
                        "\n\nDebug mode can be deactivated in this app's System Settings.")
                .setPositiveButton("System Settings", (dialog, id) -> {
                    //Load settings
                    Intent intent = new Intent(getApplicationContext(), PrefsActSystem.class);
                    intent.putExtra(getString(R.string.preftag_settings_to_load),
                            getString(R.string.preftag_menu_prefs));
                    startActivity(intent);
                })
                .setNegativeButton("Continue", (dialog, id) -> {
                    //Do nothing
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void displayLockingErrorDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(TaskManager.this);
        builder.setTitle("Warning - App not permitted to Lock Task")
                .setMessage("The App does not have permission to lock tasks." +
                        "\nThis results in the device's UI staying active during a task." +
                        "\n\nPlease give the app permission when prompted to in the Main Menu.")
                .setPositiveButton("Return", (dialog, id) -> {
                    //Load Main Menu
                    Intent intent = new Intent(getApplicationContext(), MainMenu.class);
                    startActivity(intent);
                })
                .setNegativeButton("Ignore", (dialog, id) -> {
                    //Do nothing
                });
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void initialiseRewardSystem() {
        boolean successfullyEstablished = false;
        rewardSystem.quitBt();
        rewardSystem = new RewardSystem(this, this);
        rewardSystem.connectToBluetooth();
        if (rewardSystem.bluetoothConnection | !preferencesManager.bluetooth) {
            successfullyEstablished = enableApp(true);
        }

        // Repeat if either couldn't connect or couldn't enable app
        if (successfullyEstablished) {
            tvErrors.setVisibility(View.INVISIBLE);

            // Register listener to disable tablet if bluetooth gets DC'ed
            rewardSystem.setCustomObjectListener(() ->
                    enableApp(rewardSystem.bluetoothConnection));
        } else {
            Handler handlerOne = new Handler();
            handlerOne.postDelayed(this::initialiseRewardSystem, 5000);
        }
    }

    public void startTrial(int monkId) {
        // Abort if task currently disabled
        if (!task_enabled) return;

        boolean valid_configuration = true;
        logEvent("Starting trial", false);

        Bundle bundle = new Bundle();
        bundle.putInt("currMonk", monkId);
        bundle.putInt("numTrials", trialCounter);

        Task task = null;
        switch (taskId) {
            case 0:
                task = new TaskPassiveReward();
                break;
            case 1:
                task = new TaskTrainingFullScreen();
                break;
            case 2:
                task = new TaskTrainingShrinkingCue();
                break;
            case 3:
                task = new TaskTrainingShrinkingMovingCue();
                break;
            case 4:
                task = new TaskTrainingStaticCue();
                break;
            case 5:
                task = new TaskTrainingSmallMovingCue();
                break;
            case 6:
                task = new TaskExample();
                break;
            case 7:
                task = new TaskTrainingTwoStep();
                break;
            case 8:
                task = new TaskDiscreteMaze();
                break;
            case 9:
                task = new TaskObjectDiscrimCol();
                // Check settings correct
                valid_configuration = preferencesManager.objectdiscrim_valid_config;
                break;
            case 10:
                task = new TaskObjectDiscrim();
                break;
            case 11:
                task = new TaskProgressiveRatio();
                break;
            case 12:
                task = new TaskEvidenceAccum();
                break;
            case 13:
                task = new TaskSpatialResponse();
                break;
            case 14:
                task = new TaskSequentialLearning();
                break;
            case 15:
                task = new TaskRandomDotMotion();
                break;
            case 16:
                task = new TaskDiscreteValueSpace();
                break;
            case 17:
                task = new TaskContextSequenceLearning();
                break;
            case 18:
                task = new TaskWalds();
                break;
            case 19:
                task = new TaskColoredGrating();
                break;
            default:
                new Exception("No valid task specified");
                break;
        }

        task.setFragInterfaceListener(new TaskInterface() {
            @Override
            public void resetTimer_() {
                resetTimer();
            }

            @Override
            public void trialEnded_(String outcome, double rew_scalar) {
                trialEnded(outcome, rew_scalar);
            }

            @Override
            public void logEvent_(String outcome) {
                logEvent(outcome, true);
            }

            @Override
            public void giveRewardFromTask_(int amount, boolean sound) {
                giveRewardFromTask(amount, sound);
            }

            @Override
            public void takePhotoFromTask_() {
                takePhoto();
            }

            @Override
            public void setBrightnessFromTask_(boolean bool) {
                UtilsSystem.setBrightness(bool, TaskManager.this, preferencesManager);
            }

            @Override
            public void commitTrialDataFromTask_(String overallTrialOutcome) {
                commitTrialData(overallTrialOutcome);
            }

            @Override
            public void disableTrialTimeout() {
                h0.removeCallbacksAndMessages(null);
                timerRunning = false;
            }
        });

        task.setArguments(bundle);
        fragmentTransaction.add(R.id.task_container, task, TAG_FRAGMENT_TASK);

        if (!valid_configuration) {
            // TODO: This is specific to a single task
            tvErrors.setText(preferencesManager.base_error_message
                    .concat(preferencesManager.objectdiscrim_errormessage));
        } else {
            // Start task timer first (so will still timeout if task is disabled)
            if (!timerRunning && preferencesManager.run_timer) {
                trial_timer();
            } else {
                h0.removeCallbacksAndMessages(null);
            }

            // Cancel screen dimmer timer
            h4.removeCallbacksAndMessages(null);

            // Log trial is starting
            logEvent(preferencesManager.ec_trial_started, false);
            updateTvExplanation("");
            trial_running = true;

            // Finally start the trial
            commitFragment();
        }
    }

    // Automatically restart fragmentTransaction so it is always available to use
    private void commitFragment() {
        try {
            fragmentTransaction.commit();
            fragmentTransaction = fragmentManager.beginTransaction();
        } catch (IllegalStateException e) {
            new CrashReport(e, this);
        }
    }

    private void restartApp() {
        if (preferencesManager.restartoncrash) {
            Log.d(TAG, "Restarting task");
            Intent intent = new Intent(getApplicationContext(), TaskManager.class);
            intent.putExtra("restart", true);
            intent.putExtra("tasktoload", taskId);
            final PendingIntent pendingIntent = PendingIntent.getActivity(
                    getApplicationContext(),
                    0,
                    intent,
                    PendingIntent.FLAG_ONE_SHOT);
            AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 50, pendingIntent);
        }
        System.exit(0);
    }

    public void setFaceRecogPrediction(int[] intArray) {
        if (faceRecog != null && faceRecog.instantiated_successfully) {
            faceRecogPrediction = faceRecog.idImage(intArray);
            if (faceRecogPrediction == monkeyButtonPressed) {
                // If monkey clicked it's designated menu_button
                Log.d(TAG, "Monkey pressed correct cue");
                resultMonkeyPressedTheirCue(true);
            } else {
                // If monkey clicked wrong menu_button
                Log.d(TAG, "Monkey pressed wrong cue");
                resultMonkeyPressedTheirCue(false);
            }
        } else {
            Log.d(TAG, "Error: FaceRecog not instantiated");
        }

        // Release facerecog as now ready to process another image
        faceRecogRunning = false;
    }

    private void writeSessionToDatabase() {
        Log.d(TAG, "Writing session to database");
        // Insert new entry into database
        Session session = new Session();
        session.ms_reward_given = l_rewgiven;
        session.num_corr_trials = l_numcorr;
        session.num_trials = trialCounter;
        session.date = folderManager.getBaseDate();
        MymouDatabase db = Room.databaseBuilder(this,
                MymouDatabase.class, "MymouDatabase").build();
        AsyncTask.execute(() -> {
            long id = db.userDao().insertSession(session);
            if (id == -1) {  // If sess already existed, then update the entry instead
                Log.d(TAG, "Updated preexisting session");
                db.userDao().updateSession(session);
            } else {
                Log.d(TAG, "Created new session");
            }
        });
    }

    public void commitTrialData(String overallTrialOutcome) {
        if (trialData != null) {
            // Reset trial counter if we passed midnight
            if (dateHasChanged()) {
                writeSessionToDatabase();
                trialCounter = 0;
                l_rewgiven = 0;
                l_numcorr = 0;
            }

            // Append all static variables to each line of trial data and write to file
            int length = trialData.size();
            for (int i = 0; i < length; i++) {
                // Prefix variables that were constant throughout trial
                // (trial result, which monkey, etc)
                String dataToWrite = taskId + "," + trialCounter + "," + faceRecogPrediction +
                        "," + overallTrialOutcome + "," + trialData.get(i);
                logHandler.post(new WriteDataToFile(dataToWrite, this, filename));
            }

            // Place photo in correct monkey's folder
            if (preferencesManager.facerecog) {
                // Find name of photo and old/new location
                String photo_name = folderManager.getBaseDate() + "_" + photoTimestamp + ".jpg";
                File original_photo = new File(folderManager.getImageFolder(), photo_name);
                File new_photo = new File(folderManager.getMonkeyFolder(faceRecogPrediction),
                        photo_name);

                // Copy from original_photo to new_photo
                boolean copy_successful = true;
                FileChannel inputStream = null;
                FileChannel outputStream = null;
                try {
                    inputStream = new FileInputStream(original_photo).getChannel();
                    outputStream = new FileOutputStream(new_photo).getChannel();
                    outputStream.transferFrom(inputStream, 0, inputStream.size());
                } catch (IOException e) {
                    copy_successful = false;
                    e.printStackTrace();
                } finally {
                    if (inputStream != null && outputStream != null) {
                        try {
                            inputStream.close();
                            outputStream.close();
                        } catch (IOException | NullPointerException e) {
                            copy_successful = false;
                            e.printStackTrace();
                        }
                    }
                }

                // Erase original photo if copy successful
                if (copy_successful) {
                    original_photo.delete();
                }
            }
        }

        // And now clear the list ready for the next trial
        trialData = new ArrayList<>();

        // Increment trial counter
        trialCounter++;
    }

    public void logEvent(String data, boolean from_task) {
        Log.d(TAG, "logEvent: " + data);
        tvExplanation.setText(data);

        // Seperate task logs and manager logs into different columns
        String column = "";
        if (from_task) {
            column = ",";
        }

        // Store data for logging at end of trial
        String timestamp = folderManager.getTimestamp();
        String msg = photoTimestamp + "," + timestamp + "," + column + data;
        trialData.add(msg);
    }

    // Takes selfie and checks to see if it matches with which monkey it should be
    public boolean checkMonkey(int monkId) {
        if (preferencesManager.facerecog && faceRecogRunning) {
            // Previous face recog still running
            return false;
        }

        boolean photoTaken = takePhoto();

        if (photoTaken && preferencesManager.facerecog) {
            // Photo taken, now we wait for faceRecog to return prediction
            faceRecogRunning = true;
            monkeyButtonPressed = monkId;
        }

        return photoTaken;
    }

    public boolean takePhoto() {
        if (preferencesManager.camera) {
            if (camera.camera_error) {
                // Kill camera fragment and restart it
                fragmentTransaction.remove(fragmentManager.findFragmentByTag(TAG_FRAGMENT_TASK));
                fragmentTransaction.remove(fragmentManager.findFragmentByTag(TAG_FRAGMENT_CAMERA));
                commitFragment();
                loadCamera();
                startTrial(-1);
            }

            Log.d(TAG, "takePhoto() called");
            photoTimestamp = folderManager.getTimestamp();
            return camera.captureStillPicture(photoTimestamp);
        } else {
            Log.d(TAG, "Skipping photo taking..");
            return true;
        }
    }

    //Checks if todays date is the same as the last time function was called
    public boolean dateHasChanged() {
        String todaysDate = folderManager.getBaseDate();
        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        String lastRecordedDate = sharedPref.getString("lastdate", "null");
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString("lastdate", todaysDate);
        editor.commit();
        return !todaysDate.equals(lastRecordedDate);
    }

    @Override
    public void onPause() {
        super.onPause();
        UtilsSystem.setBrightness(true, this, preferencesManager);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy() called");
        super.onDestroy();

        writeSessionToDatabase();

        // Shutdown handlers
        cancelHandlers();
        rewardSystem.quitBt();
        quitThreads();
        this.stopLockTask();
    }

    private void quitThreads() {
        try {
            logThread.quitSafely();
        } catch (NullPointerException ignored) {
        }
    }

    public boolean isAppNotInLockTaskMode() {
        // Source: https://stackoverflow.com/a/28647053
        ActivityManager activityManager;
        activityManager = (ActivityManager) this.getSystemService(Context.ACTIVITY_SERVICE);
        return activityManager.getLockTaskModeState() == ActivityManager.LOCK_TASK_MODE_NONE;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Allow user to exit task if testing mode is enabled

        if (preferencesManager.debug || isAppNotInLockTaskMode()) {
            return super.onKeyDown(keyCode, event);
        } else {
            return false;
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        return false;
    }

    @Override
    public void onClick(View view) {
        Log.d(TAG, "onClickListener called for " + view.getId());
        if (!task_enabled) return;

        if (preferencesManager.facerecog &&
                (faceRecog == null || !faceRecog.instantiated_successfully)) {
            logEvent("Waiting for facial recog to instantiate", false);
            return;
        }

        // Always disable all cues after a press as monkeys love to bash repeatedly
        disableAllCues();

        // Reset task trial_timer (used for idle timeout and calculating reaction times if desired)
        resetTimer();

        // Make screen bright
        UtilsSystem.setBrightness(true, this, preferencesManager);

        // Now decide what to do based on what menu_button pressed
        switch (view.getId()) {
            case R.id.foregroundblack:
                // Absorb any touch events while disabled
                break;
            case R.id.buttonRewardZero:
                deliverReward(0, 1);
                break;
            case R.id.buttonRewardOne:
                deliverReward(1, 1);
                break;
            case R.id.buttonRewardTwo:
                deliverReward(2, 1);
                break;
            case R.id.buttonRewardThree:
                deliverReward(3, 1);
                break;
            default:
                // If it wasn't a reward cue it must be a go cue
                checkMonkeyPressedTheirCue(view.getId());
                break;
        }
    }

    // Each monkey has it's own start cue.
    // At start of each trial make sure the monkey pressed it's own cue using the facial recognition
    private void checkMonkeyPressedTheirCue(int monkId) {
        // Take selfie
        boolean photoTaken = checkMonkey(monkId);

        if (!photoTaken) {
            // Photo not taken as camera/faceRecog wasn't ready
            // so reset go cues to let them press again
            updateTvExplanation("Error: Camera not ready!");
            UtilsTask.toggleCues(cues_Go, true);
        } else if (preferencesManager.facerecog &&
                preferencesManager.camera_to_use != this.getResources().getInteger(R.integer.TAG_CAMERA_EXTERNAL)) {
            // If photo successfully taken  (and we're not using the external camera) then do
            // nothing as wait for faceRecog to return prediction
            // setFaceRecogPrediction will ultimately call resultMonkeyPressedTheirCue
            updateTvExplanation("Photo taken, waiting for faceRecog..");
        } else {
            // If photo successfully taken then start the trial
            startTrial(monkId);
        }
    }

    public void resultMonkeyPressedTheirCue(boolean correctCuePressed) {
        // Have to put this on UI thread as it's called from faceRecog which is off main thread
        this.runOnUiThread(() -> {
            if (correctCuePressed) {
                startTrial(faceRecogPrediction);
            } else {
                MonkeyPressedWrongGoCue();
            }
        });
    }

    // Wrong Go cue selected so give short timeout
    public void MonkeyPressedWrongGoCue() {
        // Log the event
        logEvent("Monkey pressed wrong cue", false);
        commitTrialData(preferencesManager.ec_wrong_gocue_pressed);

        // Switch on red background
        findViewById(R.id.background_main)
                .setBackgroundColor(preferencesManager.timeoutbackground);

        // Switch off red background after certain delay
        h2.postDelayed(() -> {
            UtilsTask.toggleCues(cues_Go, true);
            findViewById(R.id.background_main)
                    .setBackgroundColor(preferencesManager.taskbackground);
        }, timeoutWrongGoCuePressed);
    }

    private void trialEnded(String result, double rew_scalar) {
        killTask();

        if (result.equals(preferencesManager.ec_correct_trial)) {
            l_numcorr = l_numcorr + 1;
        }

        if (!handle_feedback) {
            endOfTrial(result, 0);
        } else if (result.equals(preferencesManager.ec_correct_trial)) {
            correctTrial(rew_scalar);
            l_numcorr = l_numcorr + 1;
        } else {
            incorrectTrial(result);
        }
    }

    private void killTask() {
        if (trial_running) {
            try {
                fragmentTransaction.remove(fragmentManager.findFragmentByTag(TAG_FRAGMENT_TASK));
            } catch (NullPointerException e) {
                Log.d(TAG, "No Task loaded");
            }

            commitFragment();
            h0.removeCallbacksAndMessages(null);
            timerRunning = false;
            trial_running = false;
        }
    }

    private void incorrectTrial(String result) {
        findViewById(R.id.background_main)
                .setBackgroundColor(preferencesManager.timeoutbackground);
        endOfTrial(result, preferencesManager.timeoutduration);
    }

    private void correctTrial(double rew_scalar) {
        if (rew_scalar == 0) {
            return;
        }

        findViewById(R.id.background_main)
                .setBackgroundColor(preferencesManager.rewardbackground);

        // If only one reward channel, skip reward selection stage
        if (preferencesManager.num_reward_chans == 1) {
            deliverReward(preferencesManager.default_rew_chan, rew_scalar);
        } else {
            // Otherwise reveal reward cues
            UtilsTask.randomlyPositionCues(cues_Reward, possible_cue_locs);
            UtilsTask.toggleCues(cues_Reward, true);
            updateTvExplanation("Correct trial! Choose your reward");
        }
    }

    private void giveRewardFromTask(int reward_duration, boolean sound) {
        if (sound) {
            new SoundManager(preferencesManager).playTone();
        }

        rewardSystem.activateChannel(latestRewardChannel, reward_duration);
        l_rewgiven = l_rewgiven + reward_duration;
    }

    public void FaceRecogFinishedLoading() {
        logEvent("FaceRecog instantiated successfully", false);
    }

    private void deliverReward(int juiceChoice, double rew_scalar) {
        // Play tone
        new SoundManager(preferencesManager).playTone();

        latestRewardChannel = juiceChoice;

        final int reward_juice_duration = (int) (preferencesManager.reward_juice_duration * rew_scalar);

        updateTvExplanation("Delivering reward of " + reward_juice_duration +
                "ms on channel " + juiceChoice);

        rewardSystem.activateChannel(juiceChoice, reward_juice_duration);

        endOfTrial(preferencesManager.ec_correct_trial, preferencesManager.reward_screen_duration);
    }

    private void endOfTrial(String outcome, int newTrialDelay) {
        logEvent(outcome, false);

        commitTrialData(outcome);

        PrepareForNewTrial(newTrialDelay);
    }

    private void updateTvExplanation(String message) {
        Log.d(TAG, message);
        if (preferencesManager.debug) {
            tvExplanation.setText(message);
        }
    }

    private void resetTimer() {
        Log.d(TAG, "resetTimer");
        UtilsSystem.setBrightness(true, this, preferencesManager);
        time = 0;
    }

    // Recursive function to track task time
    private void trial_timer() {
        Log.d(TAG, "trial_timer " + time +
                " (limit =" + preferencesManager.responseduration + ")");

        time += 1000;

        // Make sure we can't have multiple timer instances
        h0.removeCallbacksAndMessages(null);

        h0.postDelayed(() -> {
            if (time > preferencesManager.responseduration) {
                Log.d(TAG, "timer Trial timeout " + time);
                resetTimer();
                timerRunning = false;

                idleTimeout();
            } else {
                trial_timer();
                timerRunning = true;
            }
        }, 1000);
    }

    private void idleTimeout() {
        updateTvExplanation("Idle timeout");

        disableAllCues();
        try {
            findViewById(R.id.background_main)
                    .setBackgroundColor(preferencesManager.timeoutbackground);
        } catch (NullPointerException e) {
            Log.d(TAG, "Couldn't find background");
        }

        trialEnded(preferencesManager.ec_trial_timeout, 0);
    }

    private void PrepareForNewTrial(int delay) {
        UtilsSystem.setBrightness(true, this, preferencesManager);

        h1.postDelayed(() -> {
            findViewById(R.id.background_main)
                    .setBackgroundColor(preferencesManager.taskbackground);
            updateTvExplanation("Waiting for trial to be started");
            // Auto start next trial if skipping go cue
            if (preferencesManager.skip_go_cue) {
                startTrial(-1);
            } else {
                UtilsTask.toggleCues(cues_Go, true);
            }
        }, delay);

        // Set screen to dim if no trial started
        h4.postDelayed(() -> UtilsSystem.setBrightness(false, TaskManager.this, preferencesManager),
                (long) preferencesManager.dimscreentime * 1000 * 60);
    }

    private void cancelHandlers() {
        Log.d(TAG, "Cancel all handlers (timer etc)");
        h0.removeCallbacksAndMessages(null);
        h1.removeCallbacksAndMessages(null);
        h2.removeCallbacksAndMessages(null);
        h3.removeCallbacksAndMessages(null);
        h4.removeCallbacksAndMessages(null);
        timerRunning = false;
    }
}
