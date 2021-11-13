package mymou.task.backend;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;

import org.apache.commons.lang3.ArrayUtils;

import mymou.preferences.PreferencesManager;

import java.util.Arrays;
import java.util.Random;

public class UtilsTask {
    // Debug
    public static String TAG = "MymouUtils";

    // Make a list of the possible locations on the screen where cues can be placed
    public static Point[] getPossibleCueLocs(Activity activity) {
        PreferencesManager preferencesManager = new PreferencesManager(activity);
        int imageWidths = preferencesManager.cue_size;
        int border = preferencesManager.cue_spacing;  // Spacing between different task objects
        int totalImageSize = imageWidths + border;

        // Find centre of screen in pixels
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screenWidth = size.x;
        int screenHeight = size.y;

        // Find possible locs along each dimension
        int[] xlocs = calculateLocs(screenWidth, totalImageSize);
        int[] ylocs = calculateLocs(screenHeight, totalImageSize);

        // Populate 1D output array with all possible locations
        Point[] locs = new Point[xlocs.length * ylocs.length];
        int i_loc = 0;
        for (int xloc : xlocs) {
            for (int yloc : ylocs) {
                locs[i_loc] = new Point(xloc, yloc);
                i_loc += 1;
            }
        }

        return locs;
    }

    // Add a colour cue to the task
    public static Button addColorCue(int id, int color, Context context, View.OnClickListener onClickListener, ConstraintLayout layout) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        Button button = new Button(context);
        button.setWidth(preferencesManager.cue_size);
        button.setHeight(preferencesManager.cue_size);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setStroke(preferencesManager.border_size, preferencesManager.border_colour);
        Log.d(TAG, "adding coloR" + color);
//        drawable.setColor(ContextCompat.getColor(context, color));
        drawable.setColor(color);
        button.setBackgroundDrawable(drawable);
        button.setId(id);
        button.setOnClickListener(onClickListener);
        layout.addView(button);
        return button;
    }

    // Add a colour cue of a particular SHAPE to the task
    public static Button addColorCue(int id, int color, Context context, View.OnClickListener onClickListener, ConstraintLayout layout, int shape) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        Button button = new Button(context);
        button.setWidth(preferencesManager.cue_size);
        button.setHeight(preferencesManager.cue_size);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(shape);
        drawable.setStroke(preferencesManager.border_size, preferencesManager.border_colour);
//        drawable.setColor(ContextCompat.getColor(context, color));
        drawable.setColor(color);
        button.setBackgroundDrawable(drawable);
        button.setId(id);
        button.setOnClickListener(onClickListener);
        layout.addView(button);
        return button;
    }

    // Add a colour cue of a particular SHAPE to the task with or without a border
    public static Button addColorCue(int id, int color, Context context, View.OnClickListener onClickListener, ConstraintLayout layout, int shape, boolean border) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        Button button = new Button(context);
        button.setWidth(preferencesManager.cue_size);
        button.setHeight(preferencesManager.cue_size);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(shape);
        if (border) {
            drawable.setStroke(preferencesManager.border_size, preferencesManager.border_colour);
        } else {
            drawable.setStroke(0, preferencesManager.border_colour);
        }
//        drawable.setColor(ContextCompat.getColor(context, color));
        drawable.setColor(color);
        button.setBackgroundDrawable(drawable);
        button.setId(id);
        button.setOnClickListener(onClickListener);
        layout.addView(button);
        return button;
    }

    // Add a mono-colour cue to the task
    public static Button addColorCue(int id, int color, Context context, View.OnClickListener onClickListener, ConstraintLayout layout, int shape, int cue_size, int border_size, int border_colour) {
        Button button = new Button(context);
        button.setWidth(cue_size);
        button.setHeight(cue_size);
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(shape);
        drawable.setStroke(border_size, border_colour);
//        drawable.setColor(ContextCompat.getColor(context, color));
        drawable.setColor(color);
        button.setBackgroundDrawable(drawable);
        button.setId(id);
        button.setOnClickListener(onClickListener);
        layout.addView(button);
        return button;
    }

    // Add an image to the task
    public static ImageButton addImageCue(int id, Context context, ConstraintLayout layout) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        ImageButton button = new ImageButton(context);
        button.setLayoutParams(new LinearLayout.LayoutParams(preferencesManager.cue_size, preferencesManager.cue_size));
        button.setId(id);
        button.setScaleType(ImageView.ScaleType.FIT_XY);
        int border = preferencesManager.border_size;
        button.setPadding(border, border, border, border);
        layout.addView(button);
        return button;
    }

    // Add a _clickable_ image to the task
    public static ImageButton addImageCue(int id, Context context, ConstraintLayout layout, View.OnClickListener onClickListener) {
        PreferencesManager preferencesManager = new PreferencesManager(context);
        ImageButton button = new ImageButton(context);
        button.setLayoutParams(new LinearLayout.LayoutParams(preferencesManager.cue_size, preferencesManager.cue_size));
        button.setId(id);
        button.setScaleType(ImageView.ScaleType.FIT_XY);
        int border = preferencesManager.border_size;
        button.setPadding(border, border, border, border);
        button.setOnClickListener(onClickListener);
        layout.addView(button);
        return button;
    }

    // Add a _clickable_ image to the task of a certain size
    public static ImageButton addImageCue(int id, Context context, ConstraintLayout layout, View.OnClickListener onClickListener, int size, int bordersize) {
        ImageButton button = new ImageButton(context);
        button.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        button.setId(id);
        button.setScaleType(ImageView.ScaleType.FIT_XY);
        button.setPadding(bordersize, bordersize, bordersize, bordersize);
        button.setOnClickListener(onClickListener);
        layout.addView(button);
        return button;
    }

    // Switches on a particular monkeys cues, and switches off other monkey's cues
    public static void toggleMonkeyCues(int monkId, Button[][] all_cues) {
        for (Button[] all_cue : all_cues) {
            toggleCues(all_cue, false);
        }
        toggleCues(all_cues[monkId], true);
    }

    // Iterates through a list of cues enabling/disabling all in list
    public static void toggleCues(Button[] buttons, boolean status) {
        for (Button cueButton : buttons) {
            UtilsTask.toggleCue(cueButton, status);
        }
    }

    // Iterates through a list of cues enabling/disabling all in list
    public static void toggleCues(ImageButton[] buttons, boolean status) {
        for (ImageButton cueButton : buttons) {
            UtilsTask.toggleCue(cueButton, status);
        }
    }

    // Fully enable/disable individual cue
    public static void toggleCue(Button button, boolean status) {
        toggleView(button, status);
        button.setClickable(status);
    }

    // Fully enable/disable individual cue
    public static void toggleCue(ImageButton button, boolean status) {
        toggleView(button, status);
        button.setClickable(status);
    }

    public static void toggleView(View view, boolean status) {
        if (status) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.GONE);
        }
        view.setEnabled(status);
    }

    public static void randomlyPositionCues(View[] cues, Activity activity) {
        randomlyPositionCues(cues, getPossibleCueLocs(activity));
    }

    public static void randomlyPositionCues(View[] cues, Point[] locs) {
        Random r = new Random();

        // Loop through and place each cue
        int indexChoice;
        for (View cue : cues) {
            indexChoice = r.nextInt(locs.length);
            cue.setX(locs[indexChoice].x);
            cue.setY(locs[indexChoice].y);
            locs = ArrayUtils.remove(locs, indexChoice);
        }
    }

    public static void randomlyPositionCue(View cue, Activity activity) {
        randomlyPositionCue(cue, getPossibleCueLocs(activity));
    }

    public static void randomlyPositionCue(View cue, Point[] locs) {
        Random r = new Random();
        int choice = r.nextInt(locs.length);
        cue.setX(locs[choice].x);
        cue.setY(locs[choice].y);
    }

    public static int[] calculateLocs(int screenLength, int totalImageSize) {
        int num_locs = screenLength / totalImageSize; // floor division

        int[] locs = new int[num_locs];

        int offset = (screenLength - (num_locs * totalImageSize)) / 2;  // Centre images on screen

        for (int i = 0; i < num_locs; i++) {
            locs[i] = offset + i * totalImageSize;
        }

        return locs;
    }

    // Place a cue in the centre of the screen
    public static void centreCue(View cue, Activity activity) {
        Point screen_size = new Point();
        activity.getWindowManager().getDefaultDisplay().getSize(screen_size);
        cue.setX((float) (screen_size.x - cue.getWidth()) / 2);
        cue.setY((float) (screen_size.y / 2) - (cue.getHeight() * 2));
    }

    // Finds a random position in the provided boolean array
    public static int chooseValueNoReplacement(boolean[] chosen_vals) {
        if (chosen_vals == null || chosen_vals.length == 0)
            new Exception("An array greater than a size of 0 was expected but not found.");

        final Random r = new Random();
        final int length = chosen_vals.length;
        final int startIndex = r.nextInt(length);
        int index = startIndex;
        boolean wrapped = false;

        // Choosing which direction to loop through the array
        // 0 = backwards, 1 = forwards
        int direction;
        int limit;
        int goTo;
        if ((r.nextInt(2) == 0)) {
            direction = -1;
            limit = -1;
            goTo = length - 1;
        } else {
            direction = 1;
            limit = length;
            goTo = 0;
        }

        while (chosen_vals[index]) {
            index += direction;
            if (wrapped && index == startIndex) {
                new Exception("A false boolean was expected but not found.");
            } else if (index == limit) {
                wrapped = true;
                index = goTo;
                if (index == startIndex)
                    new Exception("A false boolean was expected but not found.");
            }
        }

        return index;
    }
}
