package com.upreckless.support.portraitcamerasupport;

import android.graphics.SurfaceTexture;

import java.io.File;


public interface CameraSupport {
    /**
     * Start camera preview
     *
     * @param surfaceTexture surface texture of your AutoFitTextureView where you want to show preview
     * @param width          width of view
     * @param height         height of view
     * @param cameraType     type of camera you want to work(BACK/FRONT)
     * @throws RuntimeException if manifest permission CAMERA is denied
     */
    void startPreview(SurfaceTexture surfaceTexture, int width, int height, CameraSupportController.CameraType cameraType);

    /**
     * Stop camera preview and clear resources
     */
    void stopPreview();

    /**
     * Resume camera preview
     *
     * @param surfaceTexture surface texture of your AutoFitTextureView
     * @param width          width of view
     * @param height         height of view
     * @throws RuntimeException if manifest permission CAMERA is denied
     */
    void resumePreview(SurfaceTexture surfaceTexture, int width, int height);

    /**
     * Change type of current camera (BACK?FRONT)
     *
     * @param cameraType type of camera you want to change(BACK/FRONT)
     */
    void changeCameraType(CameraSupportController.CameraType cameraType);

    /**
     * Returns the current camera type(BACK/FRONT). If tou don't
     * detect camera type return null;
     *
     * @return the current CameraSupportController.CameraType
     */
    CameraSupportController.CameraType getCurrentCameraType();

    /**
     * Returns the camera id of current camera type
     *
     * @return the camera id
     */
    String getCameraId();

    /**
     * Returns <tt>true</tt> if current camera supports flash
     *
     * @return <tt>true</tt> if current camera supports flash
     */
    boolean isFlashSupported();

    /**
     * Set auto flash mode
     *
     * @param enabled <tt>true</tt> if you want to enabled
     *                default parameter <tt>false</tt>
     */

    void setAutoFlashEnabled(boolean enabled);

    /**
     * Returns <tt>true</tt> if current camera supports flash
     *
     * @return <tt>true</tt> if enabled
     */
    boolean isAutoFlashEnabled();

    /**
     * Take a picture
     *
     * @param onSupportCameraTakePictureListener callback for taking picture or getting error :)
     */
    void takePhoto(OnSupportCameraTakePictureListener onSupportCameraTakePictureListener);

    /**
     * Returns the file after saving.
     *
     * @param bytes    the byte array of picture, that you want to save.
     * @param fileName the name of your new file
     * @param filePath the path where you want to save the file
     * @return the saved file
     * @throws RuntimeException if manifest permission WRITE_EXTERNAL_STORAGE is denied
     */
    File savePicture(byte[] bytes, String fileName, String filePath);

    /**
     * Set callback for errors with camera
     *
     * @param errorListener listener for getting errors
     */
    void setErrorListener(SupportCameraErrorListener errorListener);
}
