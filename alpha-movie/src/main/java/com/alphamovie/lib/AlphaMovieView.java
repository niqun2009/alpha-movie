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

package com.alphamovie.lib;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.TypedArray;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;

import java.io.File;
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
    private MediaPlayer mediaPlayer;
    private MediaPlayer mediaPlayerBg;

    private OnVideoStartedListener onVideoStartedListener;
    private OnVideoEndedListener onVideoEndedListener;

    private boolean isSurfaceCreated;
    private boolean isDataSourceSet;

    private PlayerState state = PlayerState.NOT_PREPARED;
    private PlayerState stateBg = PlayerState.NOT_PREPARED;

    private PlayMovieThread mMovieThread;
    private PlayMovieThread mMovieBgThread;
    private String moviePath;
    private String movieBgPath;
    private Surface movieSurface;
    private Surface movieBgSurface;

    public AlphaMovieView(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (!isInEditMode()) {
            init(attrs);
        }
    }

    private void init(AttributeSet attrs) {
        setEGLContextClientVersion(GL_CONTEXT_VERSION);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        initMediaPlayer();

        renderer = new VideoRenderer();

        obtainRendererOptions(attrs);

        this.addOnSurfacePrepareListener();
        setRenderer(renderer);

        bringToFront();
        setPreserveEGLContextOnPause(true);
//        setOpaque(false);
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
//        setScreenOnWhilePlaying(true);
//        setLooping(true);

        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                state = PlayerState.PAUSED;
                if (onVideoEndedListener != null) {
                    onVideoEndedListener.onVideoEnded();
                }
            }
        });

        mediaPlayerBg = new MediaPlayer();
        mediaPlayerBg.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                stateBg = PlayerState.PAUSED;
//                if (onVideoEndedListener != null) {
//                    onVideoEndedListener.onVideoEnded();
//                }
            }
        });
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
                public void surfacePrepared(Surface surface, Surface bgSurface) {
                    isSurfaceCreated = true;
                    mediaPlayer.setSurface(surface);
                    surface.release();

                    mediaPlayerBg.setSurface(bgSurface);
                    bgSurface.release();

//                    movieSurface = surface;
//                    movieBgSurface = bgSurface;

                    if (isDataSourceSet) {
                        prepareAndStartMediaPlayer();
                    }
                }
            });
        }
    }

    private void prepareAndStartMediaPlayer() {
//        new PlayMovieThread()

//        new Thread(new Runnable() {
//            @Override
//            public void run() {
//                prepareAsync(new MediaPlayer.OnPreparedListener() {
//                    @Override
//                    public void onPrepared(MediaPlayer mp) {
//                        start();
//                    }
//                });
//            }
//        }).start();
        prepareAsync(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                start();
            }
        });
//        new Thread(new Runnable() {
//            @Override
//            public void run() {
                prepareAsyncBg(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        startBg();
                    }
                });
//            }
//        }).start();

//        mMovieThread = new PlayMovieThread(new File(moviePath), movieSurface, new SpeedControlCallback());
//        mMovieBgThread = new PlayMovieThread(new File(movieBgPath), movieBgSurface, new SpeedControlCallback());

//        movieSurface.release();
//        movieBgSurface.release();
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

    public void setVideoFromAssets(String assetsFileName, String assetsFileNameBg) {
        reset();

        try {
            AssetFileDescriptor assetFileDescriptor = getContext().getAssets().openFd(assetsFileName);
            mediaPlayer.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            retriever.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            AssetFileDescriptor assetFileDescriptorBg = getContext().getAssets().openFd(assetsFileNameBg);
            mediaPlayerBg.setDataSource(assetFileDescriptorBg.getFileDescriptor(), assetFileDescriptorBg.getStartOffset(), assetFileDescriptorBg.getLength());

            MediaMetadataRetriever retrieverBg = new MediaMetadataRetriever();
            retrieverBg.setDataSource(assetFileDescriptorBg.getFileDescriptor(), assetFileDescriptorBg.getStartOffset(), assetFileDescriptorBg.getLength());

            onDataSourceSet(retriever);

        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }

//        moviePath = getContext().getFilesDir().getAbsolutePath() + File.separator + assetsFileName;
//        movieBgPath = getContext().getFilesDir().getAbsolutePath() + File.separator + assetsFileNameBg;
//        isDataSourceSet = true;
//
//        if (isSurfaceCreated) {
//            prepareAndStartMediaPlayer();
//        }
    }

    private static class PlayMovieThread extends Thread {
        private final File mFile;
        private final Surface mSurface;
        private final SpeedControlCallback mCallback;
        private MoviePlayer mMoviePlayer;

        /**
         * Creates thread and starts execution.
         * <p>
         * The object takes ownership of the Surface, and will access it from the new thread.
         * When playback completes, the Surface will be released.
         */
        public PlayMovieThread(File file, Surface surface, SpeedControlCallback callback) {
            mFile = file;
            mSurface = surface;
            mCallback = callback;

            start();
        }

        /**
         * Asks MoviePlayer to halt playback.  Returns without waiting for playback to halt.
         * <p>
         * Call from UI thread.
         */
        public void requestStop() {
            mMoviePlayer.requestStop();
        }

        @Override
        public void run() {
            try {
                mMoviePlayer = new MoviePlayer(mFile, mSurface, mCallback);
                mMoviePlayer.setLoopMode(true);
                mMoviePlayer.play();
            } catch (IOException ioe) {
                Log.e(TAG, "movie playback failed", ioe);
            } finally {
                mSurface.release();
                Log.d(TAG, "PlayMovieThread stopping");
            }
        }
    }

//    public void setVideoByUrl(String url) {
//        reset();
//
//        try {
//            mediaPlayer.setDataSource(url);
//
//            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//            retriever.setDataSource(url, new HashMap<String, String>());
//
//            onDataSourceSet(retriever);
//
//        } catch (IOException e) {
//            Log.e(TAG, e.getMessage(), e);
//        }
//    }
//
//    public void setVideoFromFile(FileDescriptor fileDescriptor) {
//        reset();
//
//        try {
//            mediaPlayer.setDataSource(fileDescriptor);
//
//            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//            retriever.setDataSource(fileDescriptor);
//
//            onDataSourceSet(retriever);
//
//        } catch (IOException e) {
//            Log.e(TAG, e.getMessage(), e);
//        }
//    }
//
//    public void setVideoFromFile(FileDescriptor fileDescriptor, int startOffset, int endOffset) {
//        reset();
//
//        try {
//            mediaPlayer.setDataSource(fileDescriptor, startOffset, endOffset);
//
//            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//            retriever.setDataSource(fileDescriptor, startOffset, endOffset);
//
//            onDataSourceSet(retriever);
//
//        } catch (IOException e) {
//            Log.e(TAG, e.getMessage(), e);
//        }
//    }
//
//    @TargetApi(23)
//    public void setVideoFromMediaDataSource(MediaDataSource mediaDataSource) {
//        reset();
//
//        mediaPlayer.setDataSource(mediaDataSource);
//
//        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//        retriever.setDataSource(mediaDataSource);
//
//        onDataSourceSet(retriever);
//    }
//
//    public void setVideoFromUri(Context context, Uri uri) {
//        reset();
//
//        try {
//            mediaPlayer.setDataSource(context, uri);
//
//            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
//            retriever.setDataSource(context, uri);
//
//            onDataSourceSet(retriever);
//        } catch (IOException e) {
//            Log.e(TAG, e.getMessage(), e);
//        }
//    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
//        pause();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    private void prepareAsync(final MediaPlayer.OnPreparedListener onPreparedListener) {
        if (mediaPlayer != null && state == PlayerState.NOT_PREPARED
                || state == PlayerState.STOPPED) {
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    state = PlayerState.PREPARED;
                    onPreparedListener.onPrepared(mp);
                }
            });
            mediaPlayer.prepareAsync();
        }
    }

    private void prepareAsyncBg(final MediaPlayer.OnPreparedListener onPreparedListener) {
        if (mediaPlayerBg != null && stateBg == PlayerState.NOT_PREPARED
                || stateBg == PlayerState.STOPPED) {
            mediaPlayerBg.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    stateBg = PlayerState.PREPARED;
                    onPreparedListener.onPrepared(mp);
                }
            });
            mediaPlayerBg.prepareAsync();
        }
    }

    public void start() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    switch (state) {
                        case PREPARED:
                            mediaPlayer.start();
                            state = PlayerState.STARTED;
                            if (onVideoStartedListener != null) {
                                onVideoStartedListener.onVideoStarted();
                            }
                            break;
                        case PAUSED:
                            mediaPlayer.start();
                            state = PlayerState.STARTED;
                            break;
                        case STOPPED:
                            prepareAsync(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    mediaPlayer.start();
                                    state = PlayerState.STARTED;
                                    if (onVideoStartedListener != null) {
                                        onVideoStartedListener.onVideoStarted();
                                    }
                                }
                            });
                            break;
                    }
                }
            }
        }).start();

    }

    public void startBg() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayerBg != null) {
                    switch (stateBg) {
                        case PREPARED:
                            mediaPlayerBg.start();
                            stateBg = PlayerState.STARTED;
//                    if (onVideoStartedListener != null) {
//                        onVideoStartedListener.onVideoStarted();
//                    }
                            break;
                        case PAUSED:
                            mediaPlayerBg.start();
                            stateBg = PlayerState.STARTED;
                            break;
                        case STOPPED:
                            prepareAsyncBg(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    mediaPlayerBg.start();
                                    stateBg = PlayerState.STARTED;
//                            if (onVideoStartedListener != null) {
//                                onVideoStartedListener.onVideoStarted();
//                            }
                                }
                            });
                            break;
                    }
                }
            }
        }).start();

    }

    public void pause() {
        if (mediaPlayer != null && state == PlayerState.STARTED) {
            mediaPlayer.pause();
            state = PlayerState.PAUSED;
        }
    }

    public void pauseBg() {
        if (mediaPlayerBg != null && stateBg == PlayerState.STARTED) {
            mediaPlayerBg.pause();
            stateBg = PlayerState.PAUSED;
        }
    }

    public void stop() {
        if (mediaPlayer != null && (state == PlayerState.STARTED || state == PlayerState.PAUSED)) {
            mediaPlayer.stop();
            state = PlayerState.STOPPED;
        }
    }

    public void stopBg() {
        if (mediaPlayerBg != null && (stateBg == PlayerState.STARTED || stateBg == PlayerState.PAUSED)) {
            mediaPlayerBg.stop();
            stateBg = PlayerState.STOPPED;
        }
    }

    public void reset() {
        if (mediaPlayer != null && (state == PlayerState.STARTED || state == PlayerState.PAUSED ||
                state == PlayerState.STOPPED)) {
            mediaPlayer.reset();
            state = PlayerState.NOT_PREPARED;
        }

        if (mediaPlayerBg != null && (stateBg == PlayerState.STARTED || stateBg == PlayerState.PAUSED ||
                stateBg == PlayerState.STOPPED)) {
            mediaPlayerBg.reset();
            stateBg = PlayerState.NOT_PREPARED;
        }
    }

    public void release() {
        if (mediaPlayer != null) {
            mediaPlayer.release();
            state = PlayerState.RELEASE;
        }
        if (mediaPlayerBg != null) {
            mediaPlayerBg.release();
            stateBg = PlayerState.RELEASE;
        }
    }

//    public PlayerState getState() {
//        return state;
//    }
//
//    public boolean isPlaying() {
//        return state == PlayerState.STARTED;
//    }
//
//    public boolean isPaused() {
//        return state == PlayerState.PAUSED;
//    }
//
//    public boolean isStopped() {
//        return state == PlayerState.STOPPED;
//    }
//
//    public boolean isReleased() {
//        return state == PlayerState.RELEASE;
//    }
//
//    public void seekTo(int msec) {
//        mediaPlayer.seekTo(msec);
//    }
//
//    public void setLooping(boolean looping) {
//        mediaPlayer.setLooping(looping);
//    }
//
//    public int getCurrentPosition() {
//        return mediaPlayer.getCurrentPosition();
//    }
//
//    public void setScreenOnWhilePlaying(boolean screenOn) {
//        mediaPlayer.setScreenOnWhilePlaying(screenOn);
//    }
//
//    public void setOnErrorListener(MediaPlayer.OnErrorListener onErrorListener){
//        mediaPlayer.setOnErrorListener(onErrorListener);
//    }
//
//    public void setOnVideoStartedListener(OnVideoStartedListener onVideoStartedListener) {
//        this.onVideoStartedListener = onVideoStartedListener;
//    }
//
//    public void setOnVideoEndedListener(OnVideoEndedListener onVideoEndedListener) {
//        this.onVideoEndedListener = onVideoEndedListener;
//    }
//
//    public void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener onSeekCompleteListener) {
//        mediaPlayer.setOnSeekCompleteListener(onSeekCompleteListener);
//    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
    }

    public interface OnVideoStartedListener {
        void onVideoStarted();
    }

    public interface OnVideoEndedListener {
        void onVideoEnded();
    }

    private enum PlayerState {
        NOT_PREPARED, PREPARED, STARTED, PAUSED, STOPPED, RELEASE
    }
}