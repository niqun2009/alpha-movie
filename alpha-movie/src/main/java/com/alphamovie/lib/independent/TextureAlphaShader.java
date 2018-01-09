package com.alphamovie.lib.independent;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;

/**
 * Created by luotian on 2018/1/9.
 */

public class TextureAlphaShader {

    private static String TAG = "TextureAlphaShader";

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

    private final String vertexShader =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = uMVPMatrix * aPosition;\n" +
                    "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    private final String alphaShader = "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "varying vec2 vTextureCoord;\n"
            + "uniform samplerExternalOES sTexture;\n"
            + "uniform samplerExternalOES sTextureAlpha;\n"

            + "void main() {\n"
            + "  vec4 color = texture2D(sTexture, vTextureCoord);\n"
            + "  vec4 colorAlpha = texture2D(sTextureAlpha, vTextureCoord);\n"
            + "  vec4 colorTrans = vec4(0., 0., 0., 0.);\n"
            + "  gl_FragColor = mix(colorTrans, color, colorAlpha.g);\n"
            + "}\n";

    private double accuracy = 0.95;

    private String shader = alphaShader;

    private float[] mVPMatrix = new float[16];
    private float[] sTMatrix = new float[16];

    private int program;
    private int uMVPMatrixHandle;
    private int uSTMatrixHandle;
    private int aPositionHandle;
    private int aTextureHandle;

    private int textureID;
    private int textureAlphaID;
    private SurfaceTexture surfaceTexture;
    private SurfaceTexture surfaceTextureAlpha;
    private volatile boolean updateSurface = false;
    private volatile boolean updateSurfaceAlpha = false;

    private boolean isCustom;

    private float redParam = 0.0f;
    private float greenParam = 1.0f;
    private float blueParam = 0.0f;

    private OnSurfacePrepareListener onSurfacePrepareListener;

    public TextureAlphaShader() {
        triangleVertices = ByteBuffer.allocateDirect(
                triangleVerticesData.length * FLOAT_SIZE_BYTES)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        triangleVertices.put(triangleVerticesData).position(0);

        Matrix.setIdentityM(sTMatrix, 0);
    }

    public void init() {
        program = createProgram(vertexShader, resolveShader());
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
    }

    public void onDrawFrame() {
        //        synchronized(this) {
//            if (updateSurface && updateSurfaceBg && updateSurfaceAlpha) {
        Log.d("onDrawFrame", "updateTexImage called");
        surfaceTexture.updateTexImage();
        surfaceTexture.getTransformMatrix(sTMatrix);
        updateSurface = false;

        surfaceTextureAlpha.updateTexImage();
        surfaceTextureAlpha.getTransformMatrix(sTMatrix);
        updateSurfaceAlpha = false;

//            }
//        }
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
    }

    private void prepareSurface() {
        int[] textures = new int[2];
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

    private String resolveShader() {
        return isCustom ? shader : String.format(Locale.ENGLISH, alphaShader,
                redParam, greenParam, blueParam, 1 - accuracy);
    }

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
