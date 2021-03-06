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

    private boolean isSurfaceCreated;
    private boolean isDataSourceSet;

    private MoviePlayerBlob mMoviePlayerBlob;
    private MoviePlayerBlob mMoviePlayerBlobBg;
    private MoviePlayerBlob mMoviePlayerBlobAlpha;

    public class MoviePlayerBlob {
        private MediaPlayer mMediaPlayer;
        private PlayerState mState = PlayerState.NOT_PREPARED;
        private PlayMovieThread mMovieThread;
        private String mMoviePath;
        private Surface mMovieSurface;
        private OnVideoStartedListener onVideoStartedListener;
        private OnVideoEndedListener onVideoEndedListener;

        public MoviePlayerBlob(MediaPlayer mMediaPlayer) {
            this.mMediaPlayer = mMediaPlayer;
            mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    mState = PlayerState.PAUSED;
                    if (onVideoEndedListener != null) {
                        onVideoEndedListener.onVideoEnded();
                    }
                }
            });
        }

        private void prepareAsync(final MediaPlayer.OnPreparedListener onPreparedListener) {
            if (mMediaPlayer != null && mState == PlayerState.NOT_PREPARED
                    || mState == PlayerState.STOPPED) {
                mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        mState = PlayerState.PREPARED;
                        onPreparedListener.onPrepared(mp);
                    }
                });
                mMediaPlayer.prepareAsync();
            }
        }

        public void start() {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    if (mMediaPlayer != null) {
                        switch (mState) {
                            case PREPARED:
                            case STARTED:
                                mMediaPlayer.start();
                                mState = PlayerState.STARTED;
                                if (onVideoStartedListener != null) {
                                    onVideoStartedListener.onVideoStarted();
                                }
                                break;
                            case PAUSED:
                                mMediaPlayer.start();
                                mState = PlayerState.STARTED;
                                break;
                            case STOPPED:
                                prepareAsync(new MediaPlayer.OnPreparedListener() {
                                    @Override
                                    public void onPrepared(MediaPlayer mp) {
                                        mMediaPlayer.start();
                                        mState = PlayerState.STARTED;
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

        public void pause() {
            if (mMediaPlayer != null && mState == PlayerState.STARTED) {
                mMediaPlayer.pause();
                mState = PlayerState.PAUSED;
            }
        }

        public void stop() {
            if (mMediaPlayer != null && (mState == PlayerState.STARTED || mState == PlayerState.PAUSED)) {
                mMediaPlayer.stop();
                mState = PlayerState.STOPPED;
            }
        }

        public void reset() {
            if (mMediaPlayer != null && (mState == PlayerState.STARTED || mState == PlayerState.PAUSED ||
                    mState == PlayerState.STOPPED)) {
                mMediaPlayer.reset();
                mState = PlayerState.NOT_PREPARED;
            }
        }

        public void release() {
            if (mMediaPlayer != null) {
                mMediaPlayer.release();
                mState = PlayerState.RELEASE;
            }
        }

        public PlayerState getState() {
            return mState;
        }

        public void seekTo(int msec) {
            mMediaPlayer.seekTo(msec);
        }

        public void setLooping(boolean looping) {
            mMediaPlayer.setLooping(looping);
        }

        public int getCurrentPosition() {
            return mMediaPlayer.getCurrentPosition();
        }

        public void setScreenOnWhilePlaying(boolean screenOn) {
            mMediaPlayer.setScreenOnWhilePlaying(screenOn);
        }

        public void setOnErrorListener(MediaPlayer.OnErrorListener onErrorListener) {
            mMediaPlayer.setOnErrorListener(onErrorListener);
        }

        public void setOnVideoStartedListener(OnVideoStartedListener onVideoStartedListener) {
            this.onVideoStartedListener = onVideoStartedListener;
        }

        public void setOnVideoEndedListener(OnVideoEndedListener onVideoEndedListener) {
            this.onVideoEndedListener = onVideoEndedListener;
        }

        public void setOnSeekCompleteListener(MediaPlayer.OnSeekCompleteListener onSeekCompleteListener) {
            mMediaPlayer.setOnSeekCompleteListener(onSeekCompleteListener);
        }

        public MediaPlayer getMediaPlayer() {
            return mMediaPlayer;
        }

        public void setMediaPlayer(MediaPlayer mMediaPlayer) {
            this.mMediaPlayer = mMediaPlayer;
        }

        public void setState(PlayerState mState) {
            this.mState = mState;
        }
    }

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
        MediaPlayer mediaPlayer = new MediaPlayer();
//        setScreenOnWhilePlaying(true);
//        setLooping(true);

        MediaPlayer mediaPlayerBg = new MediaPlayer();
        MediaPlayer mediaPlayerAlpha = new MediaPlayer();

        mMoviePlayerBlob = new MoviePlayerBlob(mediaPlayer);
        mMoviePlayerBlobBg = new MoviePlayerBlob(mediaPlayerBg);
        mMoviePlayerBlobAlpha = new MoviePlayerBlob(mediaPlayerAlpha);
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
                public void surfacePrepared(Surface surface, Surface bgSurface, Surface alphaSurface) {
                    isSurfaceCreated = true;
                    mMoviePlayerBlob.getMediaPlayer().setSurface(surface);
                    surface.release();

                    mMoviePlayerBlobBg.getMediaPlayer().setSurface(bgSurface);
                    bgSurface.release();

                    mMoviePlayerBlobAlpha.getMediaPlayer().setSurface(alphaSurface);
                    alphaSurface.release();

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
        mMoviePlayerBlob.prepareAsync(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mMoviePlayerBlob.start();
            }
        });

        mMoviePlayerBlobBg.prepareAsync(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mMoviePlayerBlobBg.start();
            }
        });

        mMoviePlayerBlobAlpha.prepareAsync(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mMoviePlayerBlobAlpha.start();
            }
        });

//        final long[] moviePTS = {-1};
//        final long[] movieBgPTS = {-1};
//        final Object mStopLock = new Object();
//
//
//        mMovieThread = new PlayMovieThread(new File(moviePath), movieSurface, new MoviePlayer.FrameCallback() {
//            @Override
//            public void preRender(long presentationTimeUsec) {
//                Log.d(TAG, "mMovieThread preRender presentationTimeUsec : " + presentationTimeUsec);
//                synchronized (mStopLock) {
//                    if (movieBgPTS[0] == moviePTS[0]) {
//                        moviePTS[0] = presentationTimeUsec;
//                        try {
//                            mStopLock.wait();
//                        } catch (InterruptedException ie) {
//                            // discard
//                        }
//                    } else {
//                        moviePTS[0] = presentationTimeUsec;
//                        mStopLock.notifyAll();
//                    }
//                }
//            }
//
//            @Override
//            public void postRender() {
//
//            }
//
//            @Override
//            public void loopReset() {
//
//            }
//        });
//        mMovieBgThread = new PlayMovieThread(new File(movieBgPath), movieBgSurface, new MoviePlayer.FrameCallback() {
//            @Override
//            public void preRender(long presentationTimeUsec) {
//                Log.d(TAG, "mMovieBgThread == preRender presentationTimeUsec : " + presentationTimeUsec);
//                synchronized (mStopLock) {
//                    if (movieBgPTS[0] == moviePTS[0]) {
//                        movieBgPTS[0] = presentationTimeUsec;
//                        try {
//                            mStopLock.wait();
//                        } catch (InterruptedException ie) {
//                            // discard
//                        }
//                    } else {
//                        movieBgPTS[0] = presentationTimeUsec;
//                        mStopLock.notifyAll();
//                    }
//                }
//            }
//
//            @Override
//            public void postRender() {
//
//            }
//
//            @Override
//            public void loopReset() {
//
//            }
//        });

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

    public void setVideoFromAssets(String assetsFileName, String assetsFileNameBg, String assetsFileNameAlpha) {
        reset();

        try {
            AssetFileDescriptor assetFileDescriptor = getContext().getAssets().openFd(assetsFileName);
            mMoviePlayerBlob.getMediaPlayer().setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            AssetFileDescriptor assetFileDescriptorBg = getContext().getAssets().openFd(assetsFileNameBg);
            mMoviePlayerBlobBg.getMediaPlayer().setDataSource(assetFileDescriptorBg.getFileDescriptor(), assetFileDescriptorBg.getStartOffset(), assetFileDescriptorBg.getLength());

            MediaMetadataRetriever retrieverBg = new MediaMetadataRetriever();
            retrieverBg.setDataSource(assetFileDescriptorBg.getFileDescriptor(), assetFileDescriptorBg.getStartOffset(), assetFileDescriptorBg.getLength());

            AssetFileDescriptor assetFileDescriptorAlpha = getContext().getAssets().openFd(assetsFileNameAlpha);
            mMoviePlayerBlobAlpha.getMediaPlayer().setDataSource(assetFileDescriptorAlpha.getFileDescriptor(), assetFileDescriptorAlpha.getStartOffset(), assetFileDescriptorAlpha.getLength());

            onDataSourceSet(retrieverBg);

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
        private final MoviePlayer.FrameCallback mCallback;
        private MoviePlayer mMoviePlayer;

        /**
         * Creates thread and starts execution.
         * <p>
         * The object takes ownership of the Surface, and will access it from the new thread.
         * When playback completes, the Surface will be released.
         */
        public PlayMovieThread(File file, Surface surface, MoviePlayer.FrameCallback callback) {
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
        pause();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        release();
    }

    public void start() {
        mMoviePlayerBlob.start();
        mMoviePlayerBlobBg.start();
        mMoviePlayerBlobAlpha.start();
    }

    public void pause() {
        mMoviePlayerBlob.pause();
        mMoviePlayerBlobBg.pause();
        mMoviePlayerBlobAlpha.pause();
    }

    public void stop() {
        mMoviePlayerBlob.stop();
        mMoviePlayerBlobBg.stop();
        mMoviePlayerBlobAlpha.stop();
    }

    public void release() {
        mMoviePlayerBlob.release();
        mMoviePlayerBlobBg.release();
        mMoviePlayerBlobAlpha.release();
    }

    public void reset() {
        mMoviePlayerBlob.reset();
        mMoviePlayerBlobBg.reset();
        mMoviePlayerBlobAlpha.reset();
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