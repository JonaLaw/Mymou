package mymou.task.individual_tasks;

import androidx.fragment.app.Fragment;

import mymou.preferences.PreferencesManager;
import mymou.task.backend.TaskInterface;

/**
 * Parent Task Fragment
 *
 * All tasks must inherit from this Fragment
 * Enables communiation with TaskManager parent
 *
 */
public abstract class Task extends Fragment {

    public abstract void setFragInterfaceListener(TaskInterface callback);

    public void endOfTrial(boolean successfulTrial, double rew_scalar, TaskInterface callback, PreferencesManager preferencesManager) {
        String outcome;
        if (successfulTrial) {
            outcome = preferencesManager.ec_correct_trial;
        } else {
            outcome = preferencesManager.ec_incorrect_trial;
        }
        // Send outcome up to parent
        callback.trialEnded_(outcome, rew_scalar);
    }

    public void endOfTrial(boolean successfulTrial, TaskInterface callback, PreferencesManager preferencesManager) {
        String outcome;
        if (successfulTrial) {
            outcome = preferencesManager.ec_correct_trial;
        } else {
            outcome = preferencesManager.ec_incorrect_trial;
        }
        // Send outcome up to parent
        callback.trialEnded_(outcome, 1);
    }

    public void logEvent(String event, TaskInterface callback) {
        callback.logEvent_(event);
    }
}
