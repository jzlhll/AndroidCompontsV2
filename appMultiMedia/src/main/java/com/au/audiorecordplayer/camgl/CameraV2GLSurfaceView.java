package com.au.audiorecordplayer.camgl;

import android.content.Context;
import android.opengl.GLSurfaceView;

/**
 * Created by lb6905 on 2017/7/19.
 */

public class CameraV2GLSurfaceView extends GLSurfaceView {
    public static final String TAG = "Filter_CameraV2GLSurfaceView";

    public void init(CameraV2 camera, boolean isPreviewStarted) {
        setEGLContextClientVersion(2);

        var mCameraV2Renderer = new CameraV2Renderer(this, camera, isPreviewStarted);
        setRenderer(mCameraV2Renderer);
    }

    public CameraV2GLSurfaceView(Context context) {
        super(context);
    }
}
