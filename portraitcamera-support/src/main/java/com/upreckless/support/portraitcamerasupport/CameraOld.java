package com.upreckless.support.portraitcamerasupport;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.support.v4.content.ContextCompat;
import android.util.SparseIntArray;
import android.view.Surface;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@SuppressWarnings("deprication")
class CameraOld extends CameraSupportController implements CameraSupport {
    private static final int INVALID_CAMERA_ID = -1;

    private Context context;
    private AutoFitTextureView autoFitTextureView;
    private CameraType cameraType;
    private int cameraId;
    private Camera camera;
    private Camera.Parameters cameraParameters;
    private Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
    private boolean flashEnabled;
    private boolean flashSupported;
    private int rotation;
    private boolean isPreviewing;
    private Camera.Size previewSize;
    private Camera.Size pictureSize;
    private SupportCameraErrorListener errorListener;

    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    CameraOld(Context context, AutoFitTextureView autoFitTextureView) {
        super(context);
        this.autoFitTextureView = autoFitTextureView;
        this.context=context;
    }

    @Override
    public void startPreview(SurfaceTexture surfaceTexture, int width, int height, CameraSupportController.CameraType cameraType) {
       if (checkCameraPermission()) {
           this.enable();
           this.cameraType = cameraType;
           openCamera();
           setUpCameraOutputs(width, height);
           setUpCameraParameters();
           isPreviewing = true;
           try {
               camera.setPreviewTexture(surfaceTexture);
               camera.startPreview();
           } catch (IOException e) {
               e.printStackTrace();
           }
       }
    }

    @Override
    public void stopPreview() {
        this.disable();
        if (isPreviewing)
            if (camera != null)
                camera.stopPreview();
        isPreviewing = false;
        releaseCamera();
    }

    @Override
    public void resumePreview(SurfaceTexture surfaceTexture, int width, int height) {
        stopPreview();
        startPreview(surfaceTexture, width, height, cameraType);
    }

    @Override
    public void changeCameraType(CameraSupportController.CameraType cameraType) {
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
        return String.valueOf(cameraId);
    }

    @Override
    public boolean isFlashSupported() {
        return flashSupported;
    }

    @Override
    public void setAutoFlashEnabled(boolean enabled) {
        flashEnabled = enabled;
        setUpCameraParameters();
    }

    @Override
    public boolean isAutoFlashEnabled() {
        return flashEnabled;
    }

    @Override
    public void takePhoto(OnSupportCameraTakePictureListener onSupportCameraTakePictureListener) {
        this.onSupportCameraTakePictureListener = onSupportCameraTakePictureListener;
        if (isPreviewing) {
            cameraParameters.setRotation(getRotation());
            camera.setParameters(cameraParameters);
            if (cameraType == CameraType.BACK) {
                camera.cancelAutoFocus();
                camera.autoFocus(new Camera.AutoFocusCallback() {
                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        takePicture();
                    }
                });
            }
            if (cameraType == CameraType.FRONT) takePicture();
        }
    }

    @Override
    public void setErrorListener(SupportCameraErrorListener errorListener) {
        this.errorListener=errorListener;
    }

    @Override
    public void onSupportOrientationChanged(int orientation) {
        this.rotation = orientation;
    }

    @Override
    public CameraType getCameraType() {
        return cameraType;
    }

    private void openCamera() {
        chooseCamera();
        if (cameraId==INVALID_CAMERA_ID) throwError("Cannot detect camera");
        if (camera != null) releaseCamera();
        camera = Camera.open(cameraId);
        cameraParameters = camera.getParameters();
        checkFlash();
        camera.setDisplayOrientation(getDisplayOrientation());
    }

    private void releaseCamera() {
        if (camera != null) {
            camera.release();
            camera = null;
        }
    }

    private void setUpCameraParameters() {
        cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
        cameraParameters.setPictureSize(pictureSize.width, pictureSize.height);
        cameraParameters.setJpegQuality(90);
        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        cameraParameters.setFlashMode(flashSupported && flashEnabled ?
                Camera.Parameters.FLASH_MODE_ON : Camera.Parameters.FLASH_MODE_OFF);
        camera.setParameters(cameraParameters);
    }

    private void setUpCameraOutputs(int width, int height) {
        List<Camera.Size> pictureSizeList = camera.getParameters().getSupportedPictureSizes();
        pictureSize = pictureSizeList.get(0);
        for (int i = 1; i < pictureSizeList.size(); i++) {
            if ((pictureSizeList.get(i).width * pictureSizeList.get(i).height) > (pictureSize.width * pictureSize.height))
                pictureSize = pictureSizeList.get(i);
        }
        List<Camera.Size> previewSizeList = camera.getParameters().getSupportedPreviewSizes();
        int desiredWidth;
        int desiredHeight;
        if (rotation == 90 || rotation == 270) {
            desiredWidth = height;
            desiredHeight = width;
        } else {
            desiredWidth = width;
            desiredHeight = height;
        }
        previewSize = getOptimalSize(desiredWidth, desiredHeight, previewSizeList);
    }

    private Camera.Size getOptimalSize(int desiredWidth, int desiredHeight, List<Camera.Size> sizeList) {
        Camera.Size res = sizeList.get(0);
        for (Camera.Size size :
                sizeList) {
            if (desiredWidth <= size.width && desiredHeight <= size.height)
                return size;
        }
        return res;
    }

    private void chooseCamera() {
        Map<CameraType, Integer> cameraTypeMap = new HashMap<>();
        cameraTypeMap.put(CameraType.BACK, Camera.CameraInfo.CAMERA_FACING_BACK);
        cameraTypeMap.put(CameraType.FRONT, Camera.CameraInfo.CAMERA_FACING_FRONT);
        for (int i = 0; i < Camera.getNumberOfCameras(); i++) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == cameraTypeMap.get(cameraType)) {
                cameraId = i;
                return;
            }
        }
        cameraId = INVALID_CAMERA_ID;
    }

    private void checkFlash() {
        if (camera == null || cameraParameters == null || cameraParameters.getFlashMode() == null) {
            flashSupported = false;
            return;
        }
        List<String> supportedFlashModes = cameraParameters.getSupportedFlashModes();
        if (supportedFlashModes == null || supportedFlashModes.isEmpty() ||
                (supportedFlashModes.size() == 1 && supportedFlashModes.get(0).equals(Camera.Parameters.FLASH_MODE_OFF))) {
            flashSupported = false;
            return;
        }
        flashSupported = true;
    }

    private void takePicture() {
        camera.takePicture(null, null, new Camera.PictureCallback() {
            @Override
            public void onPictureTaken(byte[] data, Camera camera) {
                if (onSupportCameraTakePictureListener != null)
                    onSupportCameraTakePictureListener.onPicture(data);
                camera.startPreview();
            }
        });
    }

    private int getRotation() {
        if (cameraType == CameraType.BACK )
            setOrientations(90, 180, 270, 0);
        if (cameraType == CameraType.FRONT )
            setOrientations(90, 0, 270, 180);
        return (ORIENTATIONS.get(rotation) + cameraInfo.orientation + 270) % 360;
    }

    private void setOrientations(int x1, int x2, int x3, int x4) {
        ORIENTATIONS.append(Surface.ROTATION_0, x1);
        ORIENTATIONS.append(Surface.ROTATION_90, x2);
        ORIENTATIONS.append(Surface.ROTATION_180, x3);
        ORIENTATIONS.append(Surface.ROTATION_270, x4);
    }

    private int getDisplayOrientation() {
        if (cameraType == CameraType.BACK)
            return (90 + cameraInfo.orientation + 270) % 360;
        else return (270 + cameraInfo.orientation + 270) % 360;
    }

    private void throwError(String message){
        if (errorListener!=null)
            errorListener.onError(message);
        else throw new RuntimeException(message);
    }
    private boolean checkCameraPermission(){
        int permission= ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA);
        if (permission== PackageManager.PERMISSION_GRANTED) return true;
        else throwError("Check camera permission!");
        return false;
    }
}
