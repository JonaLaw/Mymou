package mymou.Utils;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.widget.Button;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import mymou.R;
import mymou.preferences.PreferencesManager;

import java.util.List;
import java.util.Objects;
import java.util.StringTokenizer;

public class UtilsSystem {
    // Debug
    public static String TAG = "MymouUtilsSystem";

    public static void setBrightness(boolean bool, Context context,
                                     PreferencesManager preferencesManager) {
        if (Settings.System.canWrite(context)) {
            int brightness;
            if (bool || !preferencesManager.dimscreen) {
                brightness = 255;
            } else {
                brightness = preferencesManager.dimscreenlevel;
            }
            ContentResolver cResolver = context.getContentResolver();
            Settings.System.putInt(cResolver, Settings.System.SCREEN_BRIGHTNESS, brightness);
        }
    }

    public static void setOnClickListenerLoop(Button[] buttons, View.OnClickListener view) {
        for (Button button : buttons) {
            button.setOnClickListener(view);
        }
    }

    public static String convertIntArrayToString(int[] list) {
        // String out = Arrays.toString(list);
        StringBuilder str = new StringBuilder();
        for (int s : list) {
            str.append(s).append(",");
        }
        return str.toString();
    }

    public static int[] loadIntArray(String tag, SharedPreferences prefs, Context context) {
        String defaultArr;
        try {
            defaultArr = getDefaultArr(tag, context);
        } catch (Exception e) {
            defaultArr = "";
            Log.e(TAG, e.getMessage());
        }

        Log.d(TAG, tag + "  " + defaultArr);
        String savedString = prefs.getString(tag, defaultArr);
        Log.d(TAG, "Loaded " + savedString + "from " + tag);
        StringTokenizer st = new StringTokenizer(savedString, ",");
        int n = st.countTokens();
        int[] savedList = new int[n];
        for (int i = 0; i < n; i++) {
            savedList[i] = Integer.parseInt(st.nextToken());
        }
        return savedList;
    }

    // Get default colour values for ColourPicker (as specified in Strings.xml)
    public static String getDefaultArr(String tag, Context context) throws Exception {
        if (tag.equals(context.getResources().getString(R.string.preftag_gocuecolors))) {
            return context.getResources().getString(R.string.default_gocue_colours);
        }
        if (tag.equals(context.getResources().getString(R.string.preftag_od_num_corr_cues))) {
            return context.getResources().getString(R.string.default_objdis_corr_colours);
        }
        if (tag.equals(context.getResources().getString(R.string.preftag_od_num_incorr_cues))) {
            return context.getResources().getString(R.string.default_objdis_incorr_colours);
        }
        if (tag.equals("two_prev_cols_incorr") | tag.equals("two_prev_cols_corr")) {
            return "doesn't matter what this default string is as " +
                    "this will only be called if there is a stored value";
        }

        throw new Exception("Invalid tag specified");
    }

    // Calculate the position to put view in the centre of the screen
    public static Point getCropDefaultXandY(Activity activity, int camera_width) {
        int default_y = 200;  // At top of screen
        // Get size of screen for centring views
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int default_x = ((size.x - camera_width) / 2);
        // default_x = (size.x) / 2) - camera_width;
        return new Point(default_x, default_y);
    }

    // Calculate how big to scale the camera view so that it fits neatly in the screen
    public static int getCropScale(Activity activity, int camera_width, int camera_height) {
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        int screen_width = size.x;
        int margin = 100;
        int x_scale = (screen_width - margin * 2) / camera_width;

        int screen_height = size.y / 2;
        int y_scale = screen_height / camera_height;

        return Math.min(x_scale, y_scale);
    }

    public static void addGraph(GraphView graph, LineGraphSeries<DataPoint> series,
                                String xlab, String ylab, int num_sessions) {
        graph.addSeries(series);

        GridLabelRenderer glr = graph.getGridLabelRenderer();
        glr.setTextSize(30);
        glr.setVerticalAxisTitle(ylab);
        glr.setHorizontalAxisTitle(xlab);
        glr.setHorizontalAxisTitleTextSize(35);
        glr.setVerticalAxisTitleTextSize(35);
        glr.setPadding(30);
        series.setColor(Color.WHITE);
        series.setThickness(5);
        series.setDataPointsRadius(10);
        series.setDrawDataPoints(true);

        // X limits
        double edge_buffer = 0.1;
        graph.getViewport().setMaxX(num_sessions - 1 + edge_buffer);
        graph.getViewport().setMinX(-edge_buffer);
        graph.getViewport().setXAxisBoundsManual(true);
        glr.setNumHorizontalLabels(num_sessions);
    }

    public static boolean[] getBooleanFalseArray(int n) {
        boolean[] out = new boolean[n];
        for (int i = 0; i < n; i++) {
            out[i] = false;
        }
        return out;
    }

    public static int[] getIndexArray(int n) {
        int[] indexArray = new int[n];
        for (int i = 0; i < n; i++) {
            indexArray[i] = i;
        }
        return indexArray;
    }

    public static CameraCharacteristics getCameraSelected(int camera_to_use, Activity mActivity) throws CameraAccessException {
        CameraManager cameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        CameraCharacteristics cameraCharacteristics;
        for (String cameraId : cameraManager.getCameraIdList()) {
            // Find the camera that has been selected to use
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (Objects.requireNonNull(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING)) ==
                    camera_to_use) {
                return cameraCharacteristics;
            }
        }
        throw new NullPointerException("cannot find the camera that was selected");
    }

    public static boolean isDisplayRotatedComparedToCamera(CameraCharacteristics cameraCharacteristics, Activity mActivity) {
        final Display display = mActivity.getWindowManager().getDefaultDisplay();
        final int totalRotation = (Objects.requireNonNull(cameraCharacteristics.
                get(CameraCharacteristics.SENSOR_ORIENTATION)) + display.getRotation() + 360) % 360;
        return totalRotation == 90 || totalRotation == 270;
    }

    public static Point getDisplaySize(Activity mActivity) {
        final Display display = mActivity.getWindowManager().getDefaultDisplay();
        Point usableDisplaySize = new Point();
        display.getSize(usableDisplaySize);
        return usableDisplaySize;
    }

    // Compares two areas and returns true if rhs is smaller
    public static boolean cameraCompareAreas(Size lhs, Size rhs) {
        // We cast here to ensure the multiplications won't overflow
        return Long.signum((long) rhs.getWidth() * rhs.getHeight() -
                (long) lhs.getWidth() * lhs.getHeight()) < 0;
    }

    public static int getIndexMinResolution(List<Size> sizes) {
        Size smallest = sizes.get(0);
        int i_smallest = 0;
        for (int i = 1; i < sizes.size(); i++) {
            if (cameraCompareAreas(smallest, sizes.get(i))) {
                i_smallest = i;
                smallest = sizes.get(i);
            }
        }
        return i_smallest;
    }

    public static Size getOptimalCameraPreviewSize(Size displaySize, Size cameraResolution) {
        final int newHeight = displaySize.getWidth() * cameraResolution.getHeight() /
                cameraResolution.getWidth();
        if (newHeight <= displaySize.getHeight()) {
            return new Size(displaySize.getWidth(), newHeight);
        }

        final int newWidth = displaySize.getHeight() * cameraResolution.getWidth() /
                cameraResolution.getHeight();
        if (newWidth <= displaySize.getWidth()) {
            return new Size(newWidth, displaySize.getHeight());
        }

        Log.e(TAG, "Could not calculate an Optimal Camera Preview Size");
        return displaySize;
    }

    public static Size reverseSize(Size size) {
        return new Size(size.getHeight(), size.getWidth());
    }

    public static float convertDpToPx(float dp, Context context) {
        return dp * context.getResources().getDisplayMetrics().density;
    }

    public static float convertSpToPx(float sp, Context context) {
        return sp * context.getResources().getDisplayMetrics().scaledDensity;
    }
}
