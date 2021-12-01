package mymou.task.backend;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.legacy.app.FragmentCompat;

import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import androidx.preference.PreferenceManager;

import mymou.R;
import mymou.Utils.UtilsSystem;
import mymou.preferences.PreferencesManager;

/**
 * Camera Main
 * <p>
 * Main camera function which runs in background behind tasks
 * Instantiated by task manager
 * Will then take photos when requested (typically whenever a trial is started)
 * If configured, will run photos through face recognition and then return result to TaskManager
 *
 * @param onImageAvailable: Listener that is called whenever the camera takes a photo
 */
public class CameraMain extends Camera
        implements FragmentCompat.OnRequestPermissionsResultCallback {

    public final String TAG = "MyMouCameraMain";
    private final TaskManager taskManager;
    private PreferencesManager preferencesManager;

    //  Camera variables
    private String mCameraId;
    private TextureView mTextureView;
    private CameraCaptureSession mCaptureSession;
    private CameraDevice mCameraDevice;
    private Size mPreviewSize;
    private Size mResolution;
    private ImageReader mImageReader;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private CaptureRequest mPreviewRequest;
    private final Semaphore mCameraOpenCloseLock;

    //Background threads and variables for saving images
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private String timestamp;
    private boolean takingPhoto;

    // Error handling
    public boolean camera_error;

    // For the user to select the resolution
    public List<Size> resolutions;

    public CameraMain(TaskManager taskManager) {
        this.taskManager = taskManager;
        mCameraOpenCloseLock = new Semaphore(1);
        takingPhoto = false;
        camera_error = false;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        //super.onCreate(savedInstanceState);
        return inflater.inflate(R.layout.activity_camera_main, container, false);
    }

    // Initialisation
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {
        preferencesManager = new PreferencesManager(getContext());
        mTextureView = view.findViewById(R.id.camera_texture);

        // Detect if the crop picker or camera settings activity is running
        if (mTextureView != null && getArguments() != null &&
                !getArguments().getBoolean(Objects.requireNonNull(getContext()).
                        getResources().getString(R.string.task_mode), false)) {
            if (getArguments().getBoolean(Objects.requireNonNull(getContext()).
                    getResources().getString(R.string.crop_picker_mode), false)) {
                try {
                    setupCropPickerCameraPreviewSize();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            } else {
                try {
                    setupSettingsCameraPreviewSize();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        }

        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    private void setupCropPickerCameraPreviewSize() throws CameraAccessException {
        // Set the camera to use to the selfie camera
        preferencesManager.camera_to_use = CameraCharacteristics.LENS_FACING_FRONT;
        CameraCharacteristics cameraCharacteristics = UtilsSystem.getCameraSelected(preferencesManager.camera_to_use,
                Objects.requireNonNull(getActivity()));

        mResolution = new Size(320, 240);
        Log.d(TAG, "crop picker resolution: " + mResolution);

        // Get the display size with an offset for the app's UI on the bottom
        final Size usableDisplaySize = getDisplaySizeWithOffset(0, 320);

        setCameraPreviewSize(cameraCharacteristics, usableDisplaySize);
    }

    private void setupSettingsCameraPreviewSize() throws CameraAccessException {
        CameraCharacteristics cameraCharacteristics = UtilsSystem.getCameraSelected(preferencesManager.camera_to_use,
                Objects.requireNonNull(getActivity()));

        // Get the camera's supported resolutions and find the one that has been selected
        final StreamConfigurationMap map = Objects.requireNonNull(cameraCharacteristics.
                get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP));
        mResolution = getSelectedResolution(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)));
        Log.d(TAG, "selected resolution: " + mResolution);

        // Get the display size with an offset for the app's UI on the bottom
        final Size usableDisplaySize = getDisplaySizeWithOffset(0, 150);

        setCameraPreviewSize(cameraCharacteristics, usableDisplaySize);
    }

    private void setCameraPreviewSize(CameraCharacteristics cameraCharacteristics, Size usableDisplaySize) {
        // Get the best mPreviewSize possible
        mPreviewSize = getmPreviewSize(usableDisplaySize, cameraCharacteristics);
        Log.d(TAG, "mPreviewSize set: " + mPreviewSize);

        // Setup the camera's preview dimensions
        mTextureView.setLayoutParams(new RelativeLayout
                .LayoutParams(mPreviewSize.getWidth(), mPreviewSize.getHeight()));

        // Center the camera preview
        if (usableDisplaySize.getWidth() > mPreviewSize.getWidth()) {
            mTextureView.setX((int) ((usableDisplaySize.getWidth() - mPreviewSize.getWidth()) / 2));
        } else {
            mTextureView.setX(0);
        }

        if (usableDisplaySize.getHeight() > mPreviewSize.getHeight()) {
            mTextureView.setY((int) ((usableDisplaySize.getHeight() - mPreviewSize.getHeight()) / 2));
        } else {
            mTextureView.setY(0);
        }
        Log.d(TAG, "mTextureView moved to (" + mTextureView.getX() + ", " + mTextureView.getY() + ")");
    }

    private Size getSelectedResolution(List<Size> cameraResolutions) throws CameraAccessException {
        int default_size_index = UtilsSystem.getIndexMinResolution(cameraResolutions);
        // Find which resolution user selected
        SharedPreferences settings = PreferenceManager
                .getDefaultSharedPreferences(Objects.requireNonNull(getContext()));
        switch (preferencesManager.camera_to_use) {
            case CameraCharacteristics.LENS_FACING_BACK:
                return cameraResolutions.get(settings.getInt(getString(R.string.preftag_camera_resolution_rear), default_size_index));
            case CameraCharacteristics.LENS_FACING_FRONT:
                return cameraResolutions.get(settings.getInt(getString(R.string.preftag_camera_resolution_front), default_size_index));
            default:
                throw new CameraAccessException(preferencesManager.camera_to_use, "Couldn't find camera specified!");
        }
    }

    private Size getDisplaySizeWithOffset(int xOffset, int yOffset) {
        final Point usableDisplaySize = UtilsSystem.getDisplaySize(Objects.requireNonNull(getActivity()));
        usableDisplaySize.x -= UtilsSystem.convertDpToPx(xOffset, Objects.requireNonNull(getContext()));
        usableDisplaySize.y -= UtilsSystem.convertDpToPx(yOffset, Objects.requireNonNull(getContext()));
        Log.d(TAG, "usable display size: " + usableDisplaySize);
        return new Size(usableDisplaySize.x, usableDisplaySize.y);
    }

    private Size getmPreviewSize(Size usableDisplaySize, CameraCharacteristics cameraCharacteristics) {
        // Swap the view dimensions for calculation as needed if they are rotated relative to the sensor.
        if (UtilsSystem.isDisplayRotatedComparedToCamera(cameraCharacteristics,
                Objects.requireNonNull(getActivity()))) {
            Log.d(TAG, "the display is rotated compared to the camera sensor");
            return UtilsSystem.reverseSize(UtilsSystem.getOptimalCameraPreviewSize(
                    UtilsSystem.reverseSize(usableDisplaySize), mResolution));
        } else {
            return UtilsSystem.getOptimalCameraPreviewSize(usableDisplaySize, mResolution);
        }
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * {@link TextureView.SurfaceTextureListener} handles several lifecycle events on a
     * {@link TextureView}.
     */
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            //Wait for surface texture ready before opening camera
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            Log.e(TAG, " onError() called: " + error);
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
            camera_error = true;
        }
    };

    // "onImageAvailable" will be called when a still image is ready to be saved.
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = reader.acquireNextImage();
            Log.d(TAG, "Saving photo..");

            // On camera thread as don't want to be able to take photo while saving previous photo
            CameraSavePhoto cameraSavePhoto = new CameraSavePhoto(taskManager, image, timestamp, getContext());
            cameraSavePhoto.run();

            // Unlock camera so next photo can be taken
            takingPhoto = false;
            Log.d(TAG, "Photo saved..");
        }
    };

    // Handles events related to JPEG capture.
    private final CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }

    //No onResume as app is oneShot
    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "Camera paused");
        closeCamera();
        stopBackgroundThread();
    }

    @Override
    public void onDestroy() {
        closeCamera();
        stopBackgroundThread();
        try {
            final View cameraView = Objects.requireNonNull(getView()).findViewById(R.id.activity_camera_main);
            ((ViewGroup) cameraView.getParent()).removeView(cameraView);
        } catch (NullPointerException ignored) {
        }
        Log.d(TAG, "Camera destroyed");
        super.onDestroy();
    }

    private void setUpCameraOutputs() {
        Log.d(TAG, "setUpCameraOutputs() called");
        Activity activity = Objects.requireNonNull(getActivity());
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);

        try {
            // Iterate through to find correct camera
            CameraCharacteristics characteristics;
            StreamConfigurationMap map = null;
            for (String camera_id : manager.getCameraIdList()) {
                characteristics = manager.getCameraCharacteristics(camera_id);
                if (Objects.requireNonNull(characteristics.get(CameraCharacteristics.LENS_FACING)) ==
                        preferencesManager.camera_to_use) {
                    map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    mCameraId = camera_id;
                    break;
                }
            }
            if (map == null) {
                throw new CameraAccessException(preferencesManager.camera_to_use,
                        "Couldn't find camera specified! Should you be loading CameraExternal instead?");
            }

            // Get string list of available camera resolutions
            resolutions = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));

            // Check if the camera resolution has already been found by a camera preview activity
            if (mResolution == null) {
                mResolution = getSelectedResolution(resolutions);
            }

            mImageReader = ImageReader.newInstance(mResolution.getWidth(),
                    mResolution.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,
                    mBackgroundHandler);

            // Tell parent we've finished loading camera
            if (callback != null) {
                callback.CameraLoaded();
            }
        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        Log.d(TAG, "openCamera() called");
        setUpCameraOutputs();
        CameraManager manager = (CameraManager) Objects.requireNonNull(getActivity())
                .getSystemService(Context.CAMERA_SERVICE);

        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            try {
                manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
            } catch (SecurityException e) {
                throw new SecurityException("Camera permissions not available.", e);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != mCaptureSession) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (null != mCameraDevice) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            Toast.makeText(Objects.requireNonNull(getActivity()).getApplicationContext(),
                    "Error 3", Toast.LENGTH_LONG).show();
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        try {
            if (mBackgroundThread != null) {
                mBackgroundThread.quitSafely();
                mBackgroundThread.join();
                mBackgroundThread = null;
            }
            mBackgroundHandler = null;
        } catch (InterruptedException | NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        Log.d(TAG, "createCameraPreviewSession() called");
        try {
            SurfaceTexture texture = Objects.requireNonNull(mTextureView.getSurfaceTexture());
            Objects.requireNonNull(mPreviewSize);
            Objects.requireNonNull(mResolution);

            // Configure the size of default buffer (the preview's resolution)

            Log.d(TAG, "createCameraPreviewSession() mPreviewSize: " + mPreviewSize + ", mResolution: " + mResolution);

            // TODO: certain camera resolutions do not allow for video previews,
            //  a still image should be presented instead
            texture.setDefaultBufferSize(mResolution.getWidth(), mResolution.getHeight());
            Log.d(TAG, "camera buffer size set to mResolution: " + mResolution);


            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            // The camera is already closed
                            if (null == mCameraDevice) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {
                                // Auto focus should be continuous for camera preview.
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

                                // Mono Color
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE,
                                        CaptureRequest.CONTROL_EFFECT_MODE_MONO);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest,
                                        mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            Toast.makeText(getActivity(), "Unable to display a video feed for the selected resolution.",
                                    Toast.LENGTH_LONG).show();
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    // Say cheese
    public boolean captureStillPicture(String ts) {
        Log.d(TAG, "Capture request started at" + ts);
        // If the camera is still in process of taking previous picture it will not take another one
        // If it took multiple photos the timestamp for saving/indexing the photos would be wrong
        // Tasks need to handle this behaviour
        if (takingPhoto) {
            return false;
        }

        // Update timestamp string, which will be used to save the photo once the photo is ready
        takingPhoto = true;
        timestamp = ts;

        try {
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            // Use the same AE and AF modes as the preview.
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            //To rotate photo set angle here
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 0);

            //Set black and white
            captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE,
                    CaptureRequest.CONTROL_EFFECT_MODE_MONO);

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);

            // Photo taken successfully so return true
            Log.d(TAG, "Capture request started successfully..");
            return true;

        } catch (CameraAccessException | NullPointerException e) {
            e.printStackTrace();

            // Couldn't take photo, return false
            return false;
        }
    }

    // Add callback to enable parent activity to react when camera is loaded
    CameraInterface callback;

    public void setFragInterfaceListener(CameraInterface callback) {
        this.callback = callback;
    }
}
