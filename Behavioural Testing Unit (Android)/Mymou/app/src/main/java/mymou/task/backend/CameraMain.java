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
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import android.widget.RelativeLayout.LayoutParams;

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

    public static String TAG = "MyMouCameraMain";
    private final TaskManager taskManager;

    //  Camera variables
    private static String mCameraId;
    private static TextureView mTextureView;
    private static CameraCaptureSession mCaptureSession;
    private static CameraDevice mCameraDevice;
    private static Size mPreviewSize;
    private static ImageReader mImageReader;
    private static CaptureRequest.Builder mPreviewRequestBuilder;
    private static CaptureRequest mPreviewRequest;
    private static Semaphore mCameraOpenCloseLock = new Semaphore(1);

    //Background threads and variables for saving images
    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler;
    private static String timestamp;
    private static boolean takingPhoto = false;

    // Error handling
    public static boolean camera_error = false;

    // For the user to select the resolution
    public List<Size> resolutions;

    public CameraMain() {
        this.taskManager = null;
    }

    public CameraMain(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.d(TAG, "onCreateView() called");
        return inflater.inflate(R.layout.activity_camera_main, container, false);
    }

    // Initialisation
    @Override
    public void onViewCreated(final View view, Bundle savedInstanceState) {

        mTextureView = (TextureView) view.findViewById(R.id.camera_texture);

        // If not in task mode, we want to make the camera preview visible
        if (getArguments() != null && !getArguments().getBoolean(getContext().getResources().getString(R.string.task_mode), false)) {
            // Set image to size of photo
            int camera_width = 240;
            int camera_height = 320;
            int scale = UtilsSystem.getCropScale(getActivity(), camera_width, camera_height);
            camera_width *= scale;
            camera_height *= scale;
            Log.d(TAG, "width: " + camera_width + " height:" + camera_height + "Activity: " + getActivity());
            // Centre texture view
            Point default_position = UtilsSystem.getCropDefaultXandY(getActivity(), camera_width);
            mTextureView.setLayoutParams(new RelativeLayout.LayoutParams(camera_width, camera_height));
            LayoutParams lp = (LayoutParams) mTextureView.getLayoutParams();
            mTextureView.setLayoutParams(lp);
            mTextureView.setY(default_position.y);
            mTextureView.setX(default_position.x);
        }

        startBackgroundThread();

        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
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
            Log.d(TAG, " onError() called: " + error);
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
    private CameraCaptureSession.CaptureCallback mCaptureCallback
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


    private void setUpCameraOutputs() {
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        PreferencesManager preferencesManager = new PreferencesManager(getContext());
        try {
            // Iterate through to find correct camera
            String[] all_camera_ids = manager.getCameraIdList();
            boolean foundCamera = false;
            int camera_facing = -1, i_camera = 0;
            StreamConfigurationMap map = null;
            for (i_camera = 0; i_camera < all_camera_ids.length; i_camera++) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(all_camera_ids[i_camera]);
                map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                camera_facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (camera_facing == preferencesManager.camera_to_use) {
                    foundCamera = true;
                    break;
                }
            }
            if (!foundCamera) {
                throw new NullPointerException("Couldn't find camera specified! Should you be loading CameraExternal instead?");
            }

            // Get string list of available camera resolutions and find smallest
            resolutions = Arrays.asList(map.getOutputSizes(ImageFormat.JPEG));
            int default_size = UtilsSystem.getArgMinResolution(resolutions);

            // Find which resolution user selected
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getContext());
            int i_resolution = -1;
            switch (preferencesManager.camera_to_use) {
                case CameraCharacteristics.LENS_FACING_BACK:
                    i_resolution = settings.getInt(getString(R.string.preftag_camera_resolution_rear), default_size);
                    break;
                case CameraCharacteristics.LENS_FACING_FRONT:
                    i_resolution = settings.getInt(getString(R.string.preftag_camera_resolution_front), default_size);
                    break;
            }
            Size resolution = (Size) resolutions.get(i_resolution);

            if (getArguments() != null && getArguments().getBoolean("crop_picker", false) && resolution.getWidth() != 320) {
                Toast.makeText(getContext(), "Crop picker will only work with 320x240 resolution photos", Toast.LENGTH_LONG).show();
                getActivity().onBackPressed();
            }

            mImageReader = ImageReader.newInstance(resolution.getWidth(), resolution.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(
                    mOnImageAvailableListener, mBackgroundHandler);
            Size[] choices = map.getOutputSizes(SurfaceTexture.class);
            mPreviewSize = choices[0];
            mCameraId = all_camera_ids[i_camera];

            // Tell parent we've finished loading camera
            callback.CameraLoaded();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        Log.d(TAG, "openCamera() called");
        setUpCameraOutputs();
        Activity activity = getActivity();
        CameraManager manager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
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
            Toast.makeText(getActivity().getApplicationContext(), "Error 3", Toast.LENGTH_LONG).show();
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
            mBackgroundThread.quitSafely();
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }

    private void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder
                    = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
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
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean captureStillPicture(String ts) {
        return captureStillPictureStatic(ts);
    }

    // Say cheese
    public static boolean captureStillPictureStatic(String ts) {
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

        } catch (CameraAccessException e) {

            e.printStackTrace();

            // Couldn't take photo, return false
            return false;

        } catch (NullPointerException e) {

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
