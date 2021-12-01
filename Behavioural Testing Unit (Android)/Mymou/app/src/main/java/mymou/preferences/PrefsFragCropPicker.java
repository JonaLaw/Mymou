package mymou.preferences;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.drawable.GradientDrawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.os.Bundle;

import android.widget.SeekBar;

import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import mymou.R;

public class PrefsFragCropPicker extends Fragment implements SeekBar.OnSeekBarChangeListener {

    private final String TAG = "MymouPrefsFragCropPicker";

    private final Context mContext;
    private final Activity mActivity;
    private View mTextureView;
    private int camera_width;
    private int camera_height;
    private Point default_position;
    private String[] crop_keys;
    private SeekBar seekBarTop, seekBarBottom, seekBarRight, seekBarLeft;
    private SeekBar[] seekbars;
    private int seekbarStartPosition;
    private boolean seekbarTracking;
    private final SharedPreferences settings;

    public PrefsFragCropPicker(Context mContext, Activity mActivity) {
        this.mContext = mContext;
        this.mActivity = mActivity;
        settings = PreferenceManager.getDefaultSharedPreferences(mContext);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_prefs_frag_crop_picker, container, false);
    }

    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        mTextureView = view.findViewById(R.id.crop_picker_texture);

        seekBarTop = view.findViewById(R.id.crop_top);
        seekBarBottom = view.findViewById(R.id.crop_bottom);
        seekBarRight = view.findViewById(R.id.crop_right);
        seekBarLeft = view.findViewById(R.id.crop_left);
        seekbars = new SeekBar[]{
                seekBarTop,
                seekBarBottom,
                seekBarLeft,
                seekBarRight
        };
        crop_keys = new String[]{
                mContext.getString(R.string.preftag_crop_top),
                mContext.getString(R.string.preftag_crop_bottom),
                mContext.getString(R.string.preftag_crop_left),
                mContext.getString(R.string.preftag_crop_right)
        };
        seekbarStartPosition = 0;
        seekbarTracking = false;
        setupCrop();
    }

    private void setupCrop() {
        final View mCameraTextureView = mActivity.findViewById(R.id.camera_texture);
        Objects.requireNonNull(mCameraTextureView);
        final ViewGroup.LayoutParams cameraView = mCameraTextureView.getLayoutParams();

        camera_width = cameraView.width;
        camera_height = cameraView.height;
        default_position = new Point((int) mCameraTextureView.getX(), (int) mCameraTextureView.getY());
        Log.d(TAG, "setupCrop; camera_width: " + camera_width +
                ", camera_height: " + camera_height +
                ", default_position: " + default_position);

        seekbars[0].setProgress(settings.getInt(crop_keys[0], 0));
        seekbars[1].setProgress(settings.getInt(crop_keys[1], 100));
        seekbars[2].setProgress(settings.getInt(crop_keys[2], 0));
        seekbars[3].setProgress(settings.getInt(crop_keys[3], 100));

        // Reset crop settings if something went wrong
        if (!checkValidAdjustment()) {
            Log.d(TAG, "resetting crop values to default");
            SharedPreferences.Editor editor = settings.edit();
            editor.putInt(crop_keys[0], 0);
            editor.putInt(crop_keys[1], 100);
            editor.putInt(crop_keys[2], 0);
            editor.putInt(crop_keys[3], 100);
            editor.apply();

            seekbars[0].setProgress(0);
            seekbars[1].setProgress(100);
            seekbars[2].setProgress(0);
            seekbars[3].setProgress(100);
        }

        // Set seekbar values and add listener
        for (SeekBar seekbar : seekbars) {
            seekbar.setMax(100);
            seekbar.setOnSeekBarChangeListener(this);
        }

        // Create the crop rectangle
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setStroke(5, Color.RED);
        drawable.setColor(Color.TRANSPARENT);
        mTextureView.setBackground(drawable);

        updateImage();
    }

    // Make sure that left crop + right crop is less than total width, and same for the other dimension
    private boolean checkValidAdjustment() {
        return seekBarTop.getProgress() < seekBarBottom.getProgress() &&
                seekBarLeft.getProgress() < seekBarRight.getProgress();
    }

    // Update red overlay on screen
    private void updateImage() {
        final int crop_height = (int) ((seekBarBottom.getProgress() - seekBarTop.getProgress()) * 0.01 * camera_height);
        final int crop_width = (int) ((seekBarRight.getProgress() - seekBarLeft.getProgress()) * 0.01 * camera_width);
        mTextureView.setLayoutParams(new RelativeLayout.LayoutParams(crop_width, crop_height));
        // Shift to the right by left crop amount
        final int crop_x = default_position.x + (int) (seekBarLeft.getProgress() * 0.01 * camera_width);
        final int crop_y = default_position.y + (int) (seekBarTop.getProgress() * 0.01 * camera_height);
        mTextureView.setX(crop_x);
        mTextureView.setY(crop_y);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (checkValidAdjustment()) updateImage();
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBarFocused) {
        // Multi touch can enable multiple seekbars to be interacted with
        // so this disables all the others, likely not threadsafe
        if (seekbarTracking) return;
        seekbarTracking = true;
        for (SeekBar seekbar : seekbars) {
            if (seekbar != seekBarFocused) {
                seekbar.setEnabled(false);
            }
        }
        seekbarStartPosition = seekBarFocused.getProgress();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // Re-enable all seekbars for interaction
        seekbarTracking = false;
        for (SeekBar seekbar : seekbars) {
            seekbar.setEnabled(true);
        }

        if (seekbarStartPosition == seekBar.getProgress()) return;

        if (checkValidAdjustment()) {
            for (int i = 0; i < seekbars.length; i++) {
                if (seekbars[i] == seekBar) {
                    settings.edit().putInt(crop_keys[i], seekBar.getProgress()).apply();
                    Log.d(TAG, "updated crop key " + i + " from " +
                            seekbarStartPosition + " to " + seekBar.getProgress());
                    return;
                }
            }
            throw new NullPointerException("seekbar not found");
        } else {
            // triggers onProgressChanged()
            seekBar.setProgress(seekbarStartPosition);
            Log.d(TAG, "reset to previous seekbar values");
        }
    }
}
