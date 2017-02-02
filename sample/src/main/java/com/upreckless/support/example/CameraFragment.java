package com.upreckless.support.example;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import com.upreckless.support.portraitcamerasupport.AutoFitTextureView;
import com.upreckless.support.portraitcamerasupport.CameraSupport;
import com.upreckless.support.portraitcamerasupport.CameraSupportController;
import com.upreckless.support.portraitcamerasupport.CameraSupportFactory;
import com.upreckless.support.portraitcamerasupport.OnSupportCameraTakePictureListener;
import com.upreckless.support.portraitcamerasupport.SupportCameraErrorListener;

/**
 * Created by Royal on 02.02.2017.
 */

public class CameraFragment extends Fragment implements TextureView.SurfaceTextureListener {

    private AutoFitTextureView autoFitTextureView;
    private CameraSupport cameraSupport;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_camera, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        autoFitTextureView = (AutoFitTextureView) view.findViewById(R.id.texture);
        view.findViewById(R.id.picture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSupport.takePhoto(new OnSupportCameraTakePictureListener() {
                    @Override
                    public void onPicture(byte[] bytes) {
                        cameraSupport.savePicture(bytes, System.currentTimeMillis() + "TestPicture.jpg",
                                Environment.getExternalStorageDirectory() + "/Welcome");
                        Toast.makeText(getContext(), "TAKEN", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(String message) {
                        Log.e("ERROR", message);
                    }
                });
            }
        });
        view.findViewById(R.id.img_reselect).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSupport.changeCameraType(cameraSupport.getCurrentCameraType() == CameraSupportController.CameraType.BACK ?
                        CameraSupportController.CameraType.FRONT : CameraSupportController.CameraType.BACK);
            }
        });
        view.findViewById(R.id.img_flash).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraSupport.setAutoFlashEnabled(!cameraSupport.isAutoFlashEnabled()
                        && cameraSupport.getCurrentCameraType() == CameraSupportController.CameraType.BACK);
                ((ImageView) v).setImageDrawable(getResources().getDrawable(cameraSupport.isAutoFlashEnabled() ?
                        R.mipmap.ic_flash_auto_white_24dp : R.mipmap.ic_flash_off_white_24dp));
            }
        });
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        cameraSupport = CameraSupportFactory.getInstance().getCameraSupport(getActivity(),
                autoFitTextureView);
        cameraSupport.setErrorListener(new SupportCameraErrorListener() {
            @Override
            public void onError(String message) {
                Log.e("ERROR", message);
               
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!autoFitTextureView.isAvailable())
            autoFitTextureView.setSurfaceTextureListener(this);
        else
            cameraSupport.resumePreview(autoFitTextureView.getSurfaceTexture(),
                    autoFitTextureView.getWidth(), autoFitTextureView.getHeight());
    }

    @Override
    public void onPause() {
        super.onPause();
        cameraSupport.stopPreview();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        cameraSupport.startPreview(surface, width, height, cameraSupport.getCurrentCameraType() == null ?
                CameraSupportController.CameraType.BACK : cameraSupport.getCurrentCameraType());
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        cameraSupport.resumePreview(surface, width, height);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }
}
