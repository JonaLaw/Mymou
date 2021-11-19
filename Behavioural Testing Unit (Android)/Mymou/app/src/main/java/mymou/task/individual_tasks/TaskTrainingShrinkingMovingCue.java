package mymou.task.individual_tasks;

import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import mymou.R;
import mymou.preferences.PreferencesManager;
import mymou.task.backend.TaskInterface;
import mymou.task.backend.UtilsTask;

import java.util.Objects;
import java.util.Random;

/**
 * Training task: Shrinking and Moving Cue
 * <p>
 * Valid touch area starts as the entire screen, and gets progressively smaller
 * The cue also moves randomly around the screen
 * An idle timeout resets size of the cue to the entire screen
 * Must get specified amount of presses in a row to receive reward
 */
public class TaskTrainingShrinkingMovingCue extends Task {

    // Debug
    public final String TAG = "TaskTrainingShrinkingMovingCue";

    private PreferencesManager prefManager;

    // Task objects
    private Button cue;
    private final Point screen_size;
    private int max_x, max_y;
    private int x_length, y_length;
    private final Random r;
    private int random_reward_time, num_cue_presses_reward,
            num_cue_presses_move, num_cue_press_shrink, num_cue_scale;

    // Cue missed
    private View background_main;
    private int num_missed_presses;
    private boolean cue_missed_failure_option, cue_visible;

    private final Handler h0;  // Task trial_timer

    public TaskTrainingShrinkingMovingCue() {
        screen_size = new Point();
        r = new Random();
        h0 = new Handler();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_task_empty, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        logEvent(TAG + " started", callback);

        assignObjects();
    }

    private void assignObjects() {
        // Load preferences
        prefManager = new PreferencesManager(getContext());
        prefManager.TrainingTasks();
        prefManager.TrainingTaskThree();

        Log.d(TAG, "random rewards enabled = " + !prefManager.t_random_reward_disabled);
        Log.d(TAG, "cue missed failure enabled = " + !prefManager.t_miss_failure_disabled);

        Display display = Objects.requireNonNull(getActivity()).getWindowManager().getDefaultDisplay();
        display.getRealSize(screen_size);
        // Figure out how big to make the cue
        max_x = screen_size.x - prefManager.cue_size;
        max_y = screen_size.y - prefManager.cue_size;

        final ConstraintLayout parentTaskContainer = Objects.requireNonNull(
                Objects.requireNonNull(getView()).findViewById(R.id.parent_task_empty));

        // Create one giant cue
        cue = UtilsTask.addColorCue(0, prefManager.t_cue_colour,
                getContext(), buttonClickListener, parentTaskContainer);

        background_main = Objects.requireNonNull(getView().getRootView().findViewById(R.id.background_main));

        // Set click listener for missed cue screen presses if enabled
        if (prefManager.t_miss_failure_disabled) {
            cue_missed_failure_option = false;
        } else {
            cue_missed_failure_option = true;
            parentTaskContainer.setOnClickListener(TouchListener);
        }

        num_cue_presses_move = 0;
        num_cue_presses_reward = 0;
        num_cue_press_shrink = 0;
        num_cue_scale = 0;

        // Do these now as they wouldn't otherwise
        shrinkCue();
        positionCue();
        startCue();
    }

    private void startCueDelayed(int delayMillis) {
        if (delayMillis == 0) {
            startCue();
        } else {
            // Re-enable cue after specified delay
            h0.postDelayed(this::startCue, delayMillis);
        }
    }

    private void startCue() {
        if (num_cue_press_shrink >= prefManager.t_three_num_cue_press_shrink) {
            num_cue_press_shrink = 0;
            num_cue_scale++;

            if (prefManager.t_three_loop_cue_shrink && num_cue_scale == 11) {
                Log.d(TAG, "resetting cue shrink loop");
                num_cue_scale = 0;
            }

            shrinkCue();
        }

        if (num_cue_presses_move >= prefManager.t_num_cue_press_move) {
            positionCue();
            num_cue_presses_move = 0;
        }

        UtilsTask.toggleCue(cue, true);
        logEvent("Cue toggled on", callback);

        background_main.setBackgroundColor(prefManager.taskbackground);

        if (cue_missed_failure_option) {
            cue_visible = true;
            num_missed_presses = 0;
        }

        if (!prefManager.t_random_reward_disabled)
            startRandomRewardTimer();
    }

    private void shrinkCue() {
        // Get cue scale based on the number of consecutive cue presses
        final float scalar;
        if (num_cue_scale > 9) {
            scalar = 0;
        } else {
            scalar = (10 - num_cue_scale) / 10f;
        }

        Log.d(TAG, "num_cue_scale: " + num_cue_scale + ", scalar: " + scalar);

        // Set cue to certain size
        x_length = (int) (prefManager.cue_size + (max_x * scalar));
        y_length = (int) (prefManager.cue_size + (max_y * scalar));
        logEvent("Setting cue size to (" + x_length + ", " + y_length + ")", callback);
        cue.setWidth(x_length);
        cue.setHeight(y_length);
    }

    private void positionCue() {
        // Put cue in random location
        final int x_loc = (int) (r.nextFloat() * (screen_size.x - x_length));
        final int y_loc = (int) (r.nextFloat() * (screen_size.y - y_length));
        logEvent("Moving cue to (" + x_loc + ", " + y_loc + ")", callback);
        cue.setX(x_loc);
        cue.setY(y_loc);
    }

    private void stopCue() {
        // Always disable cues first
        UtilsTask.toggleCue(cue, false);
        if (cue_missed_failure_option) cue_visible = false;

        // Cancel random reward timer
        h0.removeCallbacksAndMessages(null);

        // Reset timer for idle timeout on each press
        callback.resetTimer_();
    }

    private void startRandomRewardTimer() {
        if (prefManager.t_random_reward_disabled) return;

        // This should never produce an error given previous checks setting t_random_reward_disabled
        random_reward_time = r.nextInt(prefManager.t_random_reward_stop_time -
                prefManager.t_random_reward_start_time) +
                prefManager.t_random_reward_start_time;

        Log.d(TAG, "starting random reward timer: " + random_reward_time);
        h0.postDelayed(this::giveRandomReward, random_reward_time);
    }

    private void giveRandomReward() {
        final String msg = "Giving random reward after " + random_reward_time + " ms";
        Log.d(TAG, msg);
        logEvent(msg, callback);
        buttonClickListener.onClick(getView());
    }

    private final View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("Cue pressed", callback);

            stopCue();
            background_main.setBackgroundColor(prefManager.rewardbackground);

            // Take photo of button press
            callback.takePhotoFromTask_();

            // Count cue presses
            num_cue_presses_move++;
            num_cue_presses_reward++;
            num_cue_press_shrink++;

            // Reward subject
            if (num_cue_presses_reward >= prefManager.t_num_cue_press_reward) {
                callback.giveRewardFromTask_(prefManager.reward_juice_duration, true);
                num_cue_presses_reward = 0;
            }

            // Log press
            callback.logEvent_(prefManager.ec_correct_trial);

            // We have to commit the event as well as the trial never actually ends
            callback.commitTrialDataFromTask_(prefManager.ec_correct_trial);

            startCueDelayed(prefManager.reward_screen_duration);
        }
    };

    private final View.OnClickListener TouchListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (!cue_visible) return;
            logEvent("Cue press miss", callback);
            num_missed_presses++;
            if (num_missed_presses >= prefManager.t_num_missed_presses) {
                logEvent("Cue press miss failure", callback);
                stopCue();
                background_main.setBackgroundColor(prefManager.timeoutbackground);
                startCueDelayed(prefManager.timeoutduration);
            }
        }
    };

    // Implement interface and listener to enable communication up to TaskManager
    TaskInterface callback;

    public void setFragInterfaceListener(TaskInterface callback) {
        this.callback = callback;
    }

    @Override
    public void onPause() {
        super.onPause();
        super.onDestroy();
        h0.removeCallbacksAndMessages(null);
    }
}
