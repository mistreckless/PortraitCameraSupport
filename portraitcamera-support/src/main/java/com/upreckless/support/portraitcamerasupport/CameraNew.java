package com.upreckless.support.portraitcamerasupport;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;


@TargetApi(Build.VERSION_CODES.LOLLIPOP)
class CameraNew extends CameraSupportController implements CameraSupport {

    private static final int MAX_PREVIEW_WIDTH = 1920;
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    private static final int STATE_PREVIEW = 0;
    private static final int STATE_WAITING_LOCK = 1;
    private static final int STATE_WAITING_PRECAPTURE = 2;
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;
    private static final int STATE_PICTURE_TAKEN = 4;
    private int state = STATE_PREVIEW;

    private Context context;
    private AutoFitTextureView autoFitTextureView;
    private boolean isPreviewing;
    private CaptureRequest request;
    private boolean flashEnabled;
    private boolean flashSupported;

    private String cameraId;
    private Size previewSize;
    private CameraDevice cameraDevice;
    private HandlerThread supportHandlerThread;
    private Handler handler;
    private Surface previewSurface;
    private CaptureRequest.Builder captureRequest;
    private CameraCaptureSession cameraCaptureSession;
    private int sensorOrientation;
    private int rotation;
    private CameraType cameraType;
    private ImageReader imageReader;
    private SupportCameraErrorListener errorListener;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    CameraNew(Context context, AutoFitTextureView autoFitTextureView) {
        super(context);
        this.context = context;
        this.autoFitTextureView = autoFitTextureView;
    }

    @Override
    public void startPreview(SurfaceTexture surface, int width, int height, CameraType cameraType) {
        if (checkCameraPermission()) {
            this.enable();
            this.cameraType = cameraType;
            startHandler();
            setUpCameraOutputs(width, height);
            openCamera(width, height);
            surface.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
            previewSurface = new Surface(surface);
        }
    }

    @Override
    public void stopPreview() {
        this.disable();
        if (isPreviewing) {
            closeCamera();
            stopHandler();
        }
    }

    @Override
    public void resumePreview(SurfaceTexture surfaceTexture, int width, int height) {
        startPreview(surfaceTexture, width, height, cameraType);
    }

    @Override
    public void changeCameraType(CameraType cameraType) {
        stopPreview();
        startPreview(autoFitTextureView.getSurfaceTexture(),
                autoFitTextureView.getWidth(), autoFitTextureView.getHeight(), cameraType);
    }

    @Override
    public CameraSupportController.CameraType getCurrentCameraType() {
        return cameraType;
    }

    @Override
    public String getCameraId() {
        return cameraId;
    }

    @Override
    public boolean isFlashSupported() {
        return flashSupported;
    }

    @Override
    public void setAutoFlashEnabled(boolean enabled) {
        flashEnabled = enabled;
        if (captureRequest != null && cameraCaptureSession != null && handler != null) {
            setAutoFlash(captureRequest);
            try {
                request = captureRequest.build();
                cameraCaptureSession.stopRepeating();
                cameraCaptureSession.setRepeatingRequest(request, captureCallback, handler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public boolean isAutoFlashEnabled() {
        return flashEnabled;
    }

    @Override
    public void takePhoto(OnSupportCameraTakePictureListener onSupportCameraTakePictureListener) {
        this.onSupportCameraTakePictureListener = onSupportCameraTakePictureListener;
        if (isPreviewing)
            lockFocus();
    }

    @Override
    public void setErrorListener(SupportCameraErrorListener errorListener) {
        this.errorListener = errorListener;
    }


    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            createPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };
    private CameraCaptureSession.StateCallback captureStateCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession session) {
            cameraCaptureSession = session;
            try {
                captureRequest.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                setAutoFlash(captureRequest);
                request = captureRequest.build();
                cameraCaptureSession.setRepeatingRequest(request, captureCallback, handler);
                isPreviewing = true;
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession session) {

        }
    };
    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            process(result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        private void process(CaptureResult result) {
            switch (state) {
                case STATE_PREVIEW:
                    //do nothing
                    break;
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null)
                        return;
                    else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            afState == CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            state = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else runPrecaptureSequence();
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED)
                        state = STATE_WAITING_NON_PRECAPTURE;
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE)
                        captureStillPicture();
                    state = STATE_PICTURE_TAKEN;
                    break;
                }
            }
        }

    };
    private ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            captureRequest.removeTarget(imageReader.getSurface());
            if (onSupportCameraTakePictureListener != null) {
                try (Image image = reader.acquireLatestImage()) {
                    ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[byteBuffer.remaining()];
                    byteBuffer.get(bytes);
                    onSupportCameraTakePictureListener.onPicture(bytes);
                }
            }
        }
    };

    private void openCamera(int width, int height) {
        transformImage(width, height);
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            manager.openCamera(cameraId, stateCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        Map<CameraType, Integer> cameraTypeMap = new HashMap<>();
        cameraTypeMap.put(CameraType.BACK, CameraCharacteristics.LENS_FACING_BACK);
        cameraTypeMap.put(CameraType.FRONT, CameraCharacteristics.LENS_FACING_FRONT);
        try {
            for (String cameraId :
                    manager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
                Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (!Objects.equals(facing, cameraTypeMap.get(cameraType)))
                    continue;
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    throwError("Camera doesn't exists " + cameraType);
                    return;
                }
                Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());

                //Image reader
                imageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(), ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(imageAvailableListener, handler);

                Integer senOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                if (senOrientation == null) {
                    throwError("Sensor orientation is null!");
                    return;
                }
                sensorOrientation = senOrientation;
                int displayRotation = ((Activity) context).getWindowManager().getDefaultDisplay().getRotation();
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (sensorOrientation == 90 || sensorOrientation == 270)
                            swappedDimensions = true;
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (sensorOrientation == 0 || sensorOrientation == 180)
                            swappedDimensions = true;
                        break;
                }
                Point displaySize = new Point();
                ((Activity) context).getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }
                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }
                previewSize = getPreviewSize(map.getOutputSizes(SurfaceTexture.class), rotatedPreviewWidth
                        , rotatedPreviewHeight, maxPreviewWidth, maxPreviewHeight, largest);
                int orientation = context.getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE)
                    autoFitTextureView.setAspectRatio(previewSize.getWidth(), previewSize.getHeight());
                else
                    autoFitTextureView.setAspectRatio(previewSize.getHeight(), previewSize.getWidth());
                this.cameraId = cameraId;
                Boolean available = cameraCharacteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                flashSupported = available == null ? false : available;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        isPreviewing = false;
        if (cameraCaptureSession != null) cameraCaptureSession.close();
        if (cameraDevice != null) cameraDevice.close();
        if (imageReader != null) imageReader.close();
        cameraCaptureSession = null;
        cameraDevice = null;
        imageReader = null;
    }

    private void createPreviewSession() {
        try {
            captureRequest = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequest.addTarget(previewSurface);
            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, imageReader.getSurface())
                    , captureStateCallback, handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private Size getPreviewSize(Size[] choices, int textureViewWidth,
                                int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {
        List<Size> bigEnough = new ArrayList<>();
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            return choices[0];
        }
    }

    private void startHandler() {
        supportHandlerThread = new HandlerThread("camera_support_handler_thread");
        supportHandlerThread.start();
        handler = new Handler(supportHandlerThread.getLooper());
    }

    private void stopHandler() {
        if (supportHandlerThread != null) {
            supportHandlerThread.quitSafely();
            try {
                supportHandlerThread.join();
                supportHandlerThread = null;
                handler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void lockFocus() {
        try {
            //isFocusLockedState = true;
            captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START);
            state = STATE_WAITING_LOCK;
            if (cameraType == CameraType.BACK)
                cameraCaptureSession.capture(captureRequest.build(), captureCallback, handler);
            else captureStillPicture();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
        try {
            //  isFocusLockedState = false;
            captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            setAutoFlash(captureRequest);
            captureRequest.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE);
            cameraCaptureSession.capture(captureRequest.build(), captureCallback, handler);
            cameraCaptureSession.setRepeatingRequest(request, captureCallback, handler);
            state = STATE_PREVIEW;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void transformImage(int width, int height) {
        Matrix matrix = new Matrix();
        int rotation = ((Activity) context).getWindowManager().getDefaultDisplay().getRotation();
        RectF textureRectF = new RectF(0, 0, width, height);
        RectF previewRectF = new RectF(0, 0, previewSize.getHeight(), previewSize.getWidth());
        float centerX = textureRectF.centerX();
        float centerY = textureRectF.centerY();
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            previewRectF.offset(centerX - previewRectF.centerX(), centerY - previewRectF.centerY());
            matrix.setRectToRect(textureRectF, previewRectF, Matrix.ScaleToFit.FILL);
            float scale = Math.max((float) height / previewSize.getHeight(), (float) width / previewSize.getWidth());
            matrix.setScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (rotation == Surface.ROTATION_180)
            matrix.postRotate(180, centerX, centerY);
        autoFitTextureView.setTransform(matrix);
    }

    private void setAutoFlash(CaptureRequest.Builder builder) {
        if (flashSupported && flashEnabled)
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_ALWAYS_FLASH);
        else
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
        builder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
    }

    private void captureStillPicture() {
        if (cameraDevice == null) return;
        try {
            final CaptureRequest.Builder captureBuilder =
                    cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(imageReader.getSurface());
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    captureRequest.get(CaptureRequest.CONTROL_AF_MODE));
            setAutoFlash(captureBuilder);
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation());
            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };
            cameraCaptureSession.stopRepeating();
            cameraCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void runPrecaptureSequence() {
        try {
            captureRequest.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            state = STATE_WAITING_PRECAPTURE;
            cameraCaptureSession.capture(captureRequest.build(), captureCallback,
                    handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private int getOrientation() {
        if (cameraType == CameraType.BACK)
            setOrientations(90, 180, 270, 0);
        if (cameraType == CameraType.FRONT)
            setOrientations(90, 0, 270, 180);

        return (ORIENTATIONS.get(rotation) + sensorOrientation + 270) % 360;
    }

    private void setOrientations(int x1, int x2, int x3, int x4) {
        ORIENTATIONS.append(Surface.ROTATION_0, x1);
        ORIENTATIONS.append(Surface.ROTATION_90, x2);
        ORIENTATIONS.append(Surface.ROTATION_180, x3);
        ORIENTATIONS.append(Surface.ROTATION_270, x4);
    }

    private void throwError(String message) {
        if (errorListener != null) errorListener.onError(message);
        else throw new RuntimeException(message);
    }

    private boolean checkCameraPermission(){
        int permission= ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
        if (permission==PackageManager.PERMISSION_GRANTED) return true;
        else throwError("Check camera permission!");
        return false;
    }

    @Override
    public void onSupportOrientationChanged(int orientation) {
        this.rotation = orientation;
    }

    @Override
    public CameraType getCameraType() {
        return cameraType;
    }

    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size o1, Size o2) {
            return Long.signum((long) o1.getWidth() * o1.getHeight() - (long) o2.getWidth() * o2.getHeight());
        }
    }

}
