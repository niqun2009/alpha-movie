/*
 * Copyright 2017 Pavel Semak
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alphamovie.lib.independent;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import com.alphamovie.lib.MediaPlayerBlob;
import com.alphamovie.lib.R;

import java.io.IOException;

@SuppressLint("ViewConstructor")
public class AlphaMovieView extends GLSurfaceView {

    private static final int GL_CONTEXT_VERSION = 2;

    private static final int NOT_DEFINED = -1;
    private static final int NOT_DEFINED_COLOR = 0;

    private static final String TAG = "VideoSurfaceView";

    private static final float VIEW_ASPECT_RATIO = 4f / 3f;
    private float videoAspectRatio = VIEW_ASPECT_RATIO;

    VideoRenderer renderer;

    private boolean isSurfaceCreated = false;
    private boolean isDataSourceSet = false;
    private boolean isMediaPlayerBlobPrepared = false;
    private boolean isMediaPlayerBlobAlphaPrepared = false;

    private MediaPlayerBlob mMediaPlayerBlob;
    private MediaPlayerBlob mMediaPlayerBlobAlpha;

    private VideoPlayer mVideoPlayer;

    public AlphaMovieView(Context context, AttributeSet attrs) {
        super(context, attrs);
        obtainRendererOptions(attrs);
        getHolder().setFormat(PixelFormat.TRANSPARENT);
        getHolder().setFormat(PixelFormat.RGBA_8888);
        setZOrderMediaOverlay(true);
        setZOrderOnTop(true);
    }

    public void init(VideoPlayer videoPlayer) {
        this.mVideoPlayer = videoPlayer;

        setEGLContextClientVersion(GL_CONTEXT_VERSION);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        initMediaPlayer();

        renderer = new VideoRenderer();

        this.addOnSurfacePrepareListener();
        setRenderer(renderer);

        bringToFront();
        setPreserveEGLContextOnPause(true);
//        setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        mVideoPlayer.addVideoPlayerLifecycleListener(new VideoPlayer.VideoPlayerLifecycleListener() {
            @Override
            public void onStart() {
                start();
            }

            @Override
            public void onPause() {
                pause();
            }

            @Override
            public void onStop() {
                stop();
            }
        });
    }

    private void initMediaPlayer() {
        MediaPlayer mediaPlayer = new MediaPlayer();
        MediaPlayer mediaPlayerAlpha = new MediaPlayer();

        mMediaPlayerBlob = new MediaPlayerBlob(mediaPlayer);
        mMediaPlayerBlobAlpha = new MediaPlayerBlob(mediaPlayerAlpha);
    }

    private void obtainRendererOptions(AttributeSet attrs) {
        if (attrs != null) {
            TypedArray arr = getContext().obtainStyledAttributes(attrs, R.styleable.AlphaMovieView);
            int alphaColor = arr.getColor(R.styleable.AlphaMovieView_alphaColor, NOT_DEFINED_COLOR);
            if (alphaColor != NOT_DEFINED_COLOR) {
                renderer.setAlphaColor(alphaColor);
            }
            String shader = arr.getString(R.styleable.AlphaMovieView_shader);
            if (shader != null) {
                renderer.setCustomShader(shader);
            }
            float accuracy = arr.getFloat(R.styleable.AlphaMovieView_accuracy, NOT_DEFINED);
            if (accuracy != NOT_DEFINED) {
                renderer.setAccuracy(accuracy);
            }
            arr.recycle();
        }
    }

    private void addOnSurfacePrepareListener() {
        if (renderer != null) {
            renderer.setOnSurfacePrepareListener(new VideoRenderer.OnSurfacePrepareListener() {
                @Override
                public void surfacePrepared(Surface surface, Surface alphaSurface) {
                    isSurfaceCreated = true;

                    mMediaPlayerBlob.getMediaPlayer().setSurface(surface);
                    surface.release();

                    mMediaPlayerBlobAlpha.getMediaPlayer().setSurface(alphaSurface);
                    alphaSurface.release();

                    if (isDataSourceSet) {
                        prepareAndStartMediaPlayer();
                    }
                }
            });
        }
    }

    public void prepareAndStartMediaPlayer() {
        if (!isDataSourceSet || !isSurfaceCreated) return;

        mMediaPlayerBlob.prepareAsync(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isMediaPlayerBlobPrepared = true;
                if (isMediaPlayerBlobAlphaPrepared) {
                    if (mOnPrepareFinishListener != null) {
                        mOnPrepareFinishListener.OnPrepareFinish();
                    }
                }
            }
        });

        mMediaPlayerBlobAlpha.prepareAsync(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                isMediaPlayerBlobAlphaPrepared = true;
                if (isMediaPlayerBlobPrepared) {
                    if (mOnPrepareFinishListener != null) {
                        mOnPrepareFinishListener.OnPrepareFinish();
                    }
                }
            }
        });

    }

    private void calculateVideoAspectRatio(int videoWidth, int videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            videoAspectRatio = (float) videoWidth / videoHeight;
        }

        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        double currentAspectRatio = (double) widthSize / heightSize;
        if (currentAspectRatio > videoAspectRatio) {
            widthSize = (int) (heightSize * videoAspectRatio);
        } else {
            heightSize = (int) (widthSize / videoAspectRatio);
        }

        super.onMeasure(MeasureSpec.makeMeasureSpec(widthSize, widthMode),
                MeasureSpec.makeMeasureSpec(heightSize, heightMode));
    }

    private void onDataSourceSet(MediaMetadataRetriever retriever) {
        int videoWidth = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
        int videoHeight = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

        calculateVideoAspectRatio(videoWidth, videoHeight);
        isDataSourceSet = true;

        if (isSurfaceCreated) {
            prepareAndStartMediaPlayer();
        }
    }

    public void setVideoFromAssets(String assetsFileName, String assetsFileNameAlpha) {
        reset();

        try {
            AssetFileDescriptor assetFileDescriptor = getContext().getAssets().openFd(assetsFileName);
            mMediaPlayerBlob.getMediaPlayer().setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            MediaMetadataRetriever retrieverBg = new MediaMetadataRetriever();
            retrieverBg.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            AssetFileDescriptor assetFileDescriptorAlpha = getContext().getAssets().openFd(assetsFileNameAlpha);
            mMediaPlayerBlobAlpha.getMediaPlayer().setDataSource(assetFileDescriptorAlpha.getFileDescriptor(), assetFileDescriptorAlpha.getStartOffset(), assetFileDescriptorAlpha.getLength());

            onDataSourceSet(retrieverBg);

        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        pause();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    public void start() {
        if (isMediaPlayerBlobAlphaPrepared && isMediaPlayerBlobPrepared) {
            mMediaPlayerBlob.start();
            mMediaPlayerBlobAlpha.start();
            mMediaPlayerBlob.setOnVideoStartedListener(new MediaPlayerBlob.OnVideoStartedListener() {
                @Override
                public void onVideoStarted() {

                }
            });
            mMediaPlayerBlobAlpha.setOnVideoStartedListener(new MediaPlayerBlob.OnVideoStartedListener() {
                @Override
                public void onVideoStarted() {

                }
            });
        }
    }

    public void pause() {
        mMediaPlayerBlob.pause();
        mMediaPlayerBlobAlpha.pause();
    }

    public void stop() {
        mMediaPlayerBlob.stop();
        mMediaPlayerBlobAlpha.stop();
    }

    public void release() {
        mMediaPlayerBlob.release();
        mMediaPlayerBlobAlpha.release();
    }

    public void reset() {
        mMediaPlayerBlob.reset();
        mMediaPlayerBlobAlpha.reset();
    }

    private OnPrepareFinishListener mOnPrepareFinishListener;

    public void setOnPrepareFinishListener(OnPrepareFinishListener mOnPrepareFinishListener) {
        this.mOnPrepareFinishListener = mOnPrepareFinishListener;
    }

    public interface OnPrepareFinishListener {
        void OnPrepareFinish();
    }

}