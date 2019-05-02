package com.example.jbutler.mymou;
import android.graphics.Point;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.app.Fragment;

// A basic object discrimination task showcasing the main features of the Mymou system:
// Uses facial recognition to deliver separate tasks to two different subjects
// Offers choice of rewards for successful trial completion

public class TaskExample extends Fragment
        implements View.OnClickListener {

    // Debug
    public static String TAG = "TaskExample";

     // Identifier for which monkey is currently playing the task
    private static int current_monkey;

    // Task objects
    private static int num_cues = 2;
    private static Button[] cues = new Button[num_cues];  // List of all trial objects for an individual monkey
    private static Button[][] cues_all = {new Button[num_cues], new Button[num_cues]};  // All cues across all monkeys

    // Event codes for trials
    private static int ec_correctTrial = 1;  // TODO: Move these to resources xml file
    private static int ec_incorrectTrial = 0;

    // Predetermined locations where cues can appear on screen, calculated by Utils.calculateCueLocations()
    private static Point[] possible_cue_locs;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_task_example, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        // Instantiate task objects
        assignObjects();

        // Load cues for specific monkey, disable cues for other monkeys
        current_monkey = getArguments().getInt("current_monkey");
        Utils.toggleMonkeyCues(current_monkey, cues_all);
        cues = cues_all[current_monkey];
        setOnClickListenerLoop(cues);

        // Randomise cue locations
        Utils.randomlyPositionCues(cues, possible_cue_locs);

    }

    private void assignObjects() {
        // Monkey 0 cues
        cues_all[0][0] = getView().findViewById(R.id.buttonCue1MonkO);
        cues_all[0][1] = getView().findViewById(R.id.buttonCue2MonkO);

        // Monkey 1's cues
        cues_all[1][0] = getView().findViewById(R.id.buttonCue1MonkV);
        cues_all[1][1] = getView().findViewById(R.id.buttonCue2MonkV);

        possible_cue_locs = new Utils().getPossibleCueLocs(getActivity());
    }


    private void setOnClickListenerLoop(Button[] buttons) {
        for (int i = 0; i < buttons.length; i++) {
            buttons[i].setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View view) {

        // Always disable all cues after a press as monkeys love to bash repeatedly
        Utils.toggleCues(cues, false);

         // Reset timer for idle timeout on each press
         ((TaskManager) getActivity()).resetTimer();

        // Now decide what to do based on what button pressed
        boolean successfulTrial = false;
        switch (view.getId()) {
            // If they pressed the correct cue, then set the bool to true
            case R.id.buttonCue1MonkO:
                successfulTrial = true;
                break;
            case R.id.buttonCue2MonkV:
                successfulTrial = true;
                break;
        }
        endOfTrial(successfulTrial);
    }

    private void endOfTrial(boolean successfulTrial) {
        int outcome;
        if (successfulTrial) {
            outcome = ec_correctTrial;
        } else {
            outcome = ec_incorrectTrial;
        }
        // Send outcome up to parent
        ((TaskManager) getActivity()).trialEnded(outcome);
    }

}
