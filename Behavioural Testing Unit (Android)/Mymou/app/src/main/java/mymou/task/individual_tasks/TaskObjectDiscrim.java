package mymou.task.individual_tasks;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;

import org.apache.commons.lang3.ArrayUtils;

import java.util.Random;

import mymou.R;
import mymou.Utils.UtilsSystem;
import mymou.preferences.PreferencesManager;
import mymou.task.backend.TaskInterface;
import mymou.task.backend.UtilsTask;

/**
 * Task Object Discrimination (stimulus)
 * <p>
 * Subjects shown a stimuli, and then must choose them from up to 3 distractors
 * <p>
 * Stimuli are taken from Brady, T. F., Konkle, T., Alvarez, G. A. and Oliva, A. (2008). Visual
 * long-term memory has a massive storage capacity for object details. Proceedings of the National
 * Academy of Sciences, USA, 105 (38), 14325-14329.
 * <p>
 * TODO: Implement logging of task variables
 */
public class TaskObjectDiscrim extends Task {
    // Debug
    public final String TAG = "TaskObjectDiscrim";

    private ImageButton[] cues;
    private int chosen_cue_id;
    private ConstraintLayout layout;
    private PreferencesManager prefManager;
    private final Handler h0 = new Handler();  // Show object
    private final Handler h1 = new Handler();  // Hide object
    private final Handler h2 = new Handler();  // Show choices

    // The stimuli
    private final int[] stims = {
            R.drawable.aabaa,
            R.drawable.aabab,
            R.drawable.aabac,
            R.drawable.aabad,
            R.drawable.aabae,
            R.drawable.aabaf,
            R.drawable.aabag,
            R.drawable.aabah,
            R.drawable.aabai,
            R.drawable.aabaj,
            R.drawable.aabak,
            R.drawable.aabal,
            R.drawable.aabam,
            R.drawable.aaban,
            R.drawable.aabao,
            R.drawable.aabap,
            R.drawable.aaaaa,
            R.drawable.aaaab,
            R.drawable.aaaac,
            R.drawable.aaaad,
            R.drawable.aaaae,
            R.drawable.aaaaf,
            R.drawable.aaaag,
            R.drawable.aaaah,
            R.drawable.aaaai,
            R.drawable.aaaaj,
    };
    private final int num_stimuli = stims.length;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_task_empty, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, Bundle savedInstanceState) {
        logEvent(TAG + " started", callback);

        assignObjects();

        startMovie(prefManager.od_num_stim);
    }

    private void startMovie(int num_steps) {
        Log.d(TAG, "Playing movie, frame: " + num_steps + "/" + prefManager.od_num_stim);
        if (num_steps > 0) {
            h0.postDelayed(() -> UtilsTask.toggleCues(cues, true),
                    prefManager.od_start_delay);

            h1.postDelayed(() -> UtilsTask.toggleCues(cues, false),
                    prefManager.od_start_delay + prefManager.od_duration_on);

            h2.postDelayed(() -> startMovie(num_steps - 1),
                    prefManager.od_start_delay +
                            prefManager.od_duration_on +
                            prefManager.od_duration_off);
        } else {
            // Choice phase
            Random r = new Random();

            // Create a list of indexes to pick from
            final int num_dirs = 4;
            int[] indexesToChooseFrom = UtilsSystem.getIndexArray(num_dirs);

            ImageButton[] choice_cues = new ImageButton[prefManager.od_num_stim + prefManager.od_num_distractors];
            // Add correct answer
            int indexChoice;
            for (int i = 0; i < cues.length; i++) {
                choice_cues[i] = UtilsTask.addImageCue(chosen_cue_id, getContext(), layout, buttonClickListener);
                choice_cues[i].setImageResource(stims[chosen_cue_id]);

                // Pick position
                indexChoice = r.nextInt(indexesToChooseFrom.length);
                positionObject(indexesToChooseFrom[indexChoice], choice_cues[i]);
                // Remove the index that was used from the available indexes
                indexesToChooseFrom = ArrayUtils.remove(indexesToChooseFrom, indexChoice);
            }

            // Now add distractors (without replacement)

            // Array to track chosen stimuli
            indexesToChooseFrom = UtilsSystem.getIndexArray(num_stimuli);
            indexesToChooseFrom = ArrayUtils.remove(indexesToChooseFrom, chosen_cue_id);

            // For each distractor
            for (int i = 0; i < prefManager.od_num_distractors; i++) {
                // Choose stimuli
                indexChoice = r.nextInt(indexesToChooseFrom.length);

                // Add cue to the UI
                choice_cues[i + prefManager.od_num_stim] = UtilsTask.addImageCue(-1, getContext(), layout, buttonClickListener);
                choice_cues[i + prefManager.od_num_stim].setImageResource(stims[indexesToChooseFrom[indexChoice]]);

                // Remove the index that was used from the available indexes
                indexesToChooseFrom = ArrayUtils.remove(indexesToChooseFrom, indexChoice);

                // choose position of cue
                indexChoice = r.nextInt(indexesToChooseFrom.length);
                positionObject(indexesToChooseFrom[indexChoice], choice_cues[i + prefManager.od_num_stim]);

                // Remove the index that was used from the available indexes
                indexesToChooseFrom = ArrayUtils.remove(indexesToChooseFrom, indexChoice);
            }
        }
    }

    private void positionObject(int pos, ImageButton cue) {
        switch (pos) {
            case 0:
                cue.setX(175);
                cue.setY(1200);
                break;
            case 1:
                cue.setX(725);
                cue.setY(300);
                break;
            case 2:
                cue.setX(725);
                cue.setY(1200);
                break;
            case 3:
                cue.setX(175);
                cue.setY(300);
                break;
        }
    }

    private void assignObjects() {
        prefManager = new PreferencesManager(getContext());
        prefManager.ObjectDiscrim();

        layout = getView().findViewById(R.id.parent_task_empty);

        // Choose cues (without replacement)
        cues = new ImageButton[prefManager.od_num_stim];
        Random r = new Random();
        chosen_cue_id = r.nextInt(num_stimuli);
        cues[0] = UtilsTask.addImageCue(chosen_cue_id, getContext(), layout);
        cues[0].setImageResource(stims[chosen_cue_id]);

        // Position cue in centre
        cues[0].setX(450);
        cues[0].setY(750);

        UtilsTask.toggleCues(cues, false);
    }

    private View.OnClickListener buttonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.d(TAG, "onClick " + view.getId());

            // Did they select the appropriate cue
            boolean correct_chosen = view.getId() == chosen_cue_id;

            endOfTrial(correct_chosen, callback, prefManager);
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        super.onDestroy();
        h0.removeCallbacksAndMessages(null);
        h1.removeCallbacksAndMessages(null);
        h2.removeCallbacksAndMessages(null);
    }

    // Implement interface and listener to enable communication up to TaskManager
    TaskInterface callback;

    public void setFragInterfaceListener(TaskInterface callback) {
        this.callback = callback;
    }
}
