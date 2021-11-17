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

import java.util.Objects;
import java.util.Random;

import mymou.R;
import mymou.preferences.PreferencesManager;
import mymou.task.backend.TaskInterface;
import mymou.task.backend.UtilsTask;

/**
 * Training task four: Small moving cue
 *
 * Cue moves randomly around the screen
 * Instead of idle timeout, it randomly gives reward and then moves the cue
 * Different to all other tasks in that it never ends a trial, and so must handle data logging itself rather than using TaskManager
 *
 */
public class TaskTrainingFourSmallMovingCue extends Task {

    // Debug
    public final String TAG = "TaskTrainingFourSmallMovingCue";

    private PreferencesManager prefManager;

    // Task objects
    private Button cue;
    private Float x_range, y_range;
    private final Random r;
    private int random_reward_time, num_cue_presses;

    // Cue missed
    private View background_main;
    private int num_missed_presses;
    private boolean cue_missed_failure_option, cue_visible;

    private final Handler h0;  // Task trial_timer

    public TaskTrainingFourSmallMovingCue() {
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

        positionCue();

        Log.d(TAG, "random rewards enabled = " + !prefManager.t_random_reward_disabled);
        randomRewardTimer(0);
    }

    private void randomRewardTimer(int time) {
        if (prefManager.t_random_reward_disabled) return;

        Log.d(TAG, "trial_timer " + time);

        // If reset then pick next reward time
        if (time == 0) {
            random_reward_time = r.nextInt(prefManager.t_random_reward_stop_time -
                    prefManager.t_random_reward_start_time);
            random_reward_time += prefManager.t_random_reward_start_time;

            Log.d(TAG, "random_reward_time: " + random_reward_time);
        }

        time += 1;
        final int time_final = time;

        h0.postDelayed(() -> {
            if (time_final > random_reward_time) {
                Log.d(TAG, "Giving random reward");
                buttonClickListener.onClick(getView());
            } else {
                randomRewardTimer(time_final);
            }
        }, 1000);
    }

    private void assignObjects() {
        // Load preferences
        prefManager = new PreferencesManager(getContext());
        prefManager.TrainingTasks();

        final ConstraintLayout parentTaskContainer = Objects.requireNonNull(getView().findViewById(R.id.parent_task_empty));

        // Create cue
        cue = UtilsTask.addColorCue(0, prefManager.t_one_screen_colour,
                getContext(), buttonClickListener, parentTaskContainer);

        // Figure out how big to make the cue
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point screen_size = new Point();
        display.getSize(screen_size);
        x_range = (float) (screen_size.x - prefManager.cue_size);
        y_range = (float) (screen_size.y - prefManager.cue_size);

        UtilsTask.toggleCue(cue, true);

        // Set click listener for missed cue screen presses
        if (prefManager.t_num_missed_presses != 0) {
            cue_missed_failure_option = true;
            cue_visible = true;
            num_missed_presses = 0;
            parentTaskContainer.setOnClickListener(TouchListener);
            background_main = Objects.requireNonNull(getView().getRootView().findViewById(R.id.background_main));
        } else {
            cue_missed_failure_option = false;
        }

        num_cue_presses = 99;  // To position it on trial start
    }

    private void positionCue() {
        if (num_cue_presses >= prefManager.t_four_num_static_cue_pos) {
            int x_loc = (int) (r.nextFloat() * x_range);
            int y_loc = (int) (r.nextFloat() * y_range);

            logEvent("Moving cue to " + x_loc + " " + y_loc, callback);

            cue.setX(x_loc);
            cue.setY(y_loc);

            num_cue_presses = 0;
        }
    }

    private final View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            logEvent("Cue clicked", callback);

            stopCue();

            // Take photo of subject
            callback.takePhotoFromTask_();

            // Count cue presses
            num_cue_presses += 1;

            // Reward subject
            callback.giveRewardFromTask_(prefManager.reward_juice_duration, true);

            // Log press
            callback.logEvent_(prefManager.ec_correct_trial);

            // We have to commit the event as well as the trial never actually ends 
            callback.commitTrialDataFromTask_(prefManager.ec_correct_trial);

            // Move cue
            positionCue();

            startCueDelayed(2000);
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

    private void stopCue() {
        // Always disable cues first
        if (cue_missed_failure_option) cue_visible = false;
        UtilsTask.toggleCue(cue, false);

        // Cancel random reward timer
        h0.removeCallbacksAndMessages(null);

        // Reset timer for idle timeout on each press
        callback.resetTimer_();
    }

    private void startCueDelayed(int delayMillis) {
        // Re-enable cue after specified delay
        h0.postDelayed(() -> {
            UtilsTask.toggleCue(cue, true);
            if (cue_missed_failure_option) {
                background_main.setBackgroundColor(prefManager.taskbackground);
                cue_visible = true;
                num_missed_presses = 0;
            }
            logEvent("Cue toggled on", callback);
            randomRewardTimer(0);
        }, delayMillis);
    }

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
