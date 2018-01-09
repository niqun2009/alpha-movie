package com.alphamovie.lib.independent;

import android.content.Context;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import com.alphamovie.lib.R;
import com.alphamovie.lib.utils.RawResourceReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Created by luotian on 2018/1/9.
 */

public class AlphaMovieTextureRender extends TextureSurfaceRenderer {

    private static final String TAG = "AlphaMovieTextureRender";

    private static final int COLOR_MAX_VALUE = 255;

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] triangleVerticesData = {
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0, 0.f, 0.f,
            1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f,  1.0f, 0, 0.f, 1.f,
            1.0f,  1.0f, 0, 1.f, 1.f,
    };

    private FloatBuffer triangleVertices;

    private double accuracy = 0.95;

    private float[] mVPMatrix = new float[16];
    private float[] sTMatrix = new float[16];

    private Context context;

    private int program;
    private int uMVPMatrixHandle;
    private int uSTMatrixHandle;
    private int aPositionHandle;
    private int aTextureHandle;

    private int[] textures;
    private int textureID;
    private int textureAlphaID;
    private SurfaceTexture surfaceTexture;
    private SurfaceTexture surfaceTextureAlpha;
    private volatile boolean updateSurface = false;
    private volatile boolean updateSurfaceAlpha = false;

    private OnSurfacePrepareListener onSurfacePrepareListener;

    private boolean isCustom;

    private float redParam = 0.0f;
    private float greenParam = 1.0f;
    private float blueParam = 0.0f;

    public AlphaMovieTextureRender(Context context, SurfaceTexture surfaceTexture, int width, int height) {
        super(surfaceTexture, width, height);
        this.context = context;
        triangleVertices = ByteBuffer.allocateDirect(
                triangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        triangleVertices.put(triangleVerticesData).position(0);
        Matrix.setIdentityM(sTMatrix, 0);
    }

    @Override
    protected boolean draw() {
        Log.d("onDrawFrame", "updateTexImage called");
        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(sTMatrix);
        updateSurface = false;

        surfaceTextureAlpha.updateTexImage();
        surfaceTextureAlpha.getTransformMatrix(sTMatrix);
        updateSurfaceAlpha = false;

        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);

        GLES20.glUseProgram(program);
        checkGlError("glUseProgram");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0);
        checkGlError("glBindTexture textureID");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureAlphaID);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTextureAlpha"), 1);
        checkGlError("glBindTexture textureAlphaID");

        triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(aPositionHandle);
        checkGlError("glEnableVertexAttribArray aPositionHandle");

        triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(aTextureHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
        checkGlError("glVertexAttribPointer aTextureHandle");
        GLES20.glEnableVertexAttribArray(aTextureHandle);
        checkGlError("glEnableVertexAttribArray aTextureHandle");

        Matrix.setIdentityM(mVPMatrix, 0);
        GLES20.glUniformMatrix4fv(uMVPMatrixHandle, 1, false, mVPMatrix, 0);
        GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, sTMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        GLES20.glFinish();
        return true;
    }

    @Override
    protected void initGLComponents() {
        final String vertexShader = RawResourceReader.readTextFileFromRawResource(context, R.raw.vetext_sharder);
//        final String fragmentShader = RawResourceReader.readTextFileFromRawResource(context, R.raw.fragment_sharder);
        program = createProgram(vertexShader, this.resolveShader());
        if (program == 0) {
            return;
        }
        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (aPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        aTextureHandle = GLES20.glGetAttribLocation(program, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (aTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        uMVPMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (uMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }

        uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (uSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }

        prepareSurface();

        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    protected void deinitGLComponents() {
        GLES20.glDeleteTextures(textures.length, textures, 0);
        GLES20.glDeleteProgram(program);
        surfaceTexture.release();
        surfaceTexture.setOnFrameAvailableListener(null);
        surfaceTextureAlpha.release();
        surfaceTextureAlpha.setOnFrameAvailableListener(null);
        context = null;
    }

    @Override
    public SurfaceTexture getVideoTexture() {
        return null;
    }

    private void prepareSurface() {
        textures = new int[2];
        GLES20.glGenTextures(2, textures, 0);

        GLES20.glUseProgram(program);
        checkGlError("glUseProgram");

        textureID = textures[0];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTexture"), 0);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        checkGlError("glBindTexture textureID");

        textureAlphaID = textures[1];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureAlphaID);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program, "sTextureAlpha"), 1);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        checkGlError("glBindTexture textureAlphaID");

        surfaceTexture = new SurfaceTexture(textureID);
        surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                Log.d("onDrawFrame", "surfaceTexture onFrameAvailable ==");
                updateSurface = true;
            }
        });
        Surface surface = new Surface(this.surfaceTexture);

        surfaceTextureAlpha = new SurfaceTexture(textureAlphaID);
        surfaceTextureAlpha.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                Log.d("onDrawFrame", "surfaceTextureAlpha onFrameAvailable ==alpha");
                updateSurfaceAlpha = true;
            }
        });
        Surface surfaceAlpha = new Surface(this.surfaceTextureAlpha);

        onSurfacePrepareListener.surfacePrepared(surface, surfaceAlpha);

        synchronized(this) {
            updateSurface = false;
            updateSurfaceAlpha = false;
        }
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    void setAlphaColor(int color) {
        redParam = (float) Color.red(color) / COLOR_MAX_VALUE;
        greenParam = (float) Color.green(color) / COLOR_MAX_VALUE;
        blueParam = (float) Color.blue(color) / COLOR_MAX_VALUE;
    }

//    void setCustomShader(String customShader) {
//        isCustom = true;
//        shader = customShader;
//    }

    void setAccuracy(double accuracy) {
        if (accuracy > 1.0) {
            accuracy = 1.0;
        } else if (accuracy < 0.0) {
            accuracy = 0.0;
        }
        this.accuracy = accuracy;
    }

    public double getAccuracy() {
        return accuracy;
    }

    private String resolveShader() {
        String alphaShader = RawResourceReader.readTextFileFromRawResource(context, R.raw.fragment_sharder);
        return String.format(Locale.ENGLISH, alphaShader,
                redParam, greenParam, blueParam, 1 - accuracy);
    }

    private void checkGlError(String op) {
        int error;
        if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new RuntimeException(op + ": glError " + error);
        }
    }

    void setOnSurfacePrepareListener(OnSurfacePrepareListener onSurfacePrepareListener) {
        this.onSurfacePrepareListener = onSurfacePrepareListener;
    }

    interface OnSurfacePrepareListener {
        void surfacePrepared(Surface surface, Surface alphaSurface);
    }
}
