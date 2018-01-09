package com.alphamovie.lib;

import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.IOException;

/**
 * Created by luotian on 2018/1/5.
 */

public class MediaPlayerBlob {

    private static final String TAG = "MediaPlayerBlob";

    private enum PlayerState {
        NOT_PREPARED, PREPARED, STARTED, PAUSED, STOPPED, RELEASE
    }

    private MediaPlayer mMediaPlayer;
    private PlayerState mState = PlayerState.NOT_PREPARED;
    private PlayMovieThread mMovieThread;
    private String mMoviePath;
    private Surface mMovieSurface;
    private OnVideoStartedListener onVideoStartedListener;
    private OnVideoEndedListener onVideoEndedListener;

    public MediaPlayerBlob(MediaPlayer mMediaPlayer) {
        this.mMediaPlayer = mMediaPlayer;
        mMediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                if (onVideoEndedListener != null) {
                    onVideoEndedListener.onVideoEnded();
                }
            }
        });
    }

    public void prepareAsync(final MediaPlayer.OnPreparedListener onPreparedListener) {
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
                            Log.i(TAG, "mMediaPlayer.start() STARTED PREPARED");
                            mState = PlayerState.STARTED;
                            if (onVideoStartedListener != null) {
                                onVideoStartedListener.onVideoStarted();
                            }
                            break;
                        case PAUSED:
                            mMediaPlayer.start();
                            Log.i(TAG, "mMediaPlayer.start() PAUSED");
                            mState = PlayerState.STARTED;
                            if (onVideoStartedListener != null) {
                                onVideoStartedListener.onVideoStarted();
                            }
                            break;
                        case STOPPED:
                            prepareAsync(new MediaPlayer.OnPreparedListener() {
                                @Override
                                public void onPrepared(MediaPlayer mp) {
                                    mMediaPlayer.start();
                                    Log.i(TAG, "mMediaPlayer.start() STOPPED");
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

    public interface OnVideoStartedListener {
        void onVideoStarted();
    }

    public interface OnVideoEndedListener {
        void onVideoEnded();
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
}
