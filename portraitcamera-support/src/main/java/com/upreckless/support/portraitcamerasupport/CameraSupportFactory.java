package com.upreckless.support.portraitcamerasupport;

import android.app.Activity;
import android.os.Build;

public class CameraSupportFactory {
    private static CameraSupportFactory instance;

    public static CameraSupportFactory getInstance() {
        if (instance == null) instance = new CameraSupportFactory();
        return instance;
    }

    private CameraSupportFactory() {
    }

    public CameraSupport getCameraSupport(Activity context, AutoFitTextureView autoFitTextureView) {
        if (Build.VERSION.SDK_INT >= 21)
            return new CameraNew(context, autoFitTextureView);
        return new CameraOld(context, autoFitTextureView);
    }

    public CameraSupport getCameraSupport(Activity context, AutoFitTextureView autoFitTextureView,
                                           CameraSupportController.CameraApi cameraApi) {
        if (cameraApi == CameraSupportController.CameraApi.NEW)
            return new CameraNew(context, autoFitTextureView);
        if (cameraApi == CameraSupportController.CameraApi.OLD)
            return new CameraOld(context, autoFitTextureView);
        throw new RuntimeException("Camera Api doesn't exists");
    }
}
