package com.upreckless.support.portraitcamerasupport;

/**
 * Created by Royal on 26.01.2017.
 */

public interface OnSupportCameraTakePictureListener {
    void onPicture(byte[] bytes);
    void onError(String message);
}
