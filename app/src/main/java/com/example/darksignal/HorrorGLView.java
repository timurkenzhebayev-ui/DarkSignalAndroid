package com.example.darksignal;

import android.content.Context;
import android.opengl.GLSurfaceView;

public class HorrorGLView extends GLSurfaceView {
    public HorrorGLView(Context context, HorrorRenderer renderer) {
        super(context);
        setEGLContextClientVersion(2);
        setRenderer(renderer);
        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        setPreserveEGLContextOnPause(true);
    }
}
