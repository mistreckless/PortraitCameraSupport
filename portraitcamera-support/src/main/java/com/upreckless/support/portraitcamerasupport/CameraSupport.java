package com.upreckless.support.portraitcamerasupport;

import android.graphics.SurfaceTexture;

import java.io.File;


public interface CameraSupport {
    void startPreview(SurfaceTexture surfaceTexture, int width, int height, CameraSupportController.CameraType cameraType);
    void stopPreview();
    void resumePreview(SurfaceTexture surfaceTexture, int width, int height);
    void changeCameraType(CameraSupportController.CameraType cameraType);
    CameraSupportController.CameraType getCurrentCameraType();
    String getCameraId();
    boolean isFlashSupported();
    void setAutoFlashEnabled(boolean enabled);
    boolean isAutoFlashEnabled();
    void takePhoto(OnSupportCameraTakePictureListener onSupportCameraTakePictureListener);
    File savePicture(byte[] bytes, String fileName, String filePath);
    void setErrorListener(SupportCameraErrorListener errorListener);
}
