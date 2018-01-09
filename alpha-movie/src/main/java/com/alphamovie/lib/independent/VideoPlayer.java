package com.alphamovie.lib.independent;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import com.alphamovie.lib.MediaPlayerBlob;
import com.alphamovie.lib.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by luotian on 2018/1/5.
 */

public class VideoPlayer {

    private SurfaceView glSurfaceView;
    private SurfaceHolder surfaceHolder;
    private MediaPlayerBlob mediaPlayerBlob;

    public VideoPlayer(SurfaceView glSurfaceView) {
        this.glSurfaceView = glSurfaceView;
        surfaceHolder = glSurfaceView.getHolder();
    }

    public void init(final String assetFileName) {
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                MediaPlayer mediaPlayer = new MediaPlayer();
                mediaPlayer.setDisplay(holder);

                mediaPlayerBlob = new MediaPlayerBlob(mediaPlayer);

                AssetFileDescriptor assetFileDescriptor = null;
                try {
                    assetFileDescriptor = glSurfaceView.getContext().getAssets().openFd(assetFileName);
                    mediaPlayerBlob.getMediaPlayer().setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

                    MediaMetadataRetriever retrieverBg = new MediaMetadataRetriever();
                    retrieverBg.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

                    int videoWidth = Integer.parseInt(retrieverBg.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
                    int videoHeight = Integer.parseInt(retrieverBg.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));

                    float videoAspectRatio = 1.0f;
                    if (videoWidth > 0 && videoHeight > 0) {
                        videoAspectRatio = (float) videoWidth / videoHeight;
                    }

                    ViewGroup.LayoutParams layoutParams = (ViewGroup.LayoutParams) glSurfaceView.getLayoutParams();
                    layoutParams.width = Utils.getScreenWidth((Activity) glSurfaceView.getContext());
                    layoutParams.height = (int) (((float)layoutParams.width) / videoAspectRatio);
                    glSurfaceView.setLayoutParams(layoutParams);

                    mediaPlayerBlob.prepareAsync(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            if (mOnPrepareFinishListener != null) {
                                mOnPrepareFinishListener.OnPrepareFinish();
                            }
                        }
                    });

                    mediaPlayerBlob.setOnVideoStartedListener(new MediaPlayerBlob.OnVideoStartedListener() {
                        @Override
                        public void onVideoStarted() {
                            notifyOnVideoStart();
                        }
                    });

                    mediaPlayerBlob.setOnVideoEndedListener(new MediaPlayerBlob.OnVideoEndedListener() {
                        @Override
                        public void onVideoEnded() {
                            notifyOnVideoStop();
                        }
                    });

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

    }

    public void play() {
        mediaPlayerBlob.start();
    }

    public void pause() {
        mediaPlayerBlob.pause();
        notifyOnVideoPause();
    }

    public void stop() {
        mediaPlayerBlob.stop();
        notifyOnVideoStop();
    }

    public interface VideoPlayerLifecycleListener {
        void onStart();
        void onPause();
        void onStop();
    }

    private List<VideoPlayerLifecycleListener> lifecycleListeners = new ArrayList<>();

    public void addVideoPlayerLifecycleListener(VideoPlayerLifecycleListener lifecycleListener) {
        if (lifecycleListener == null) return;
        lifecycleListeners.add(lifecycleListener);
    }

    public void removeVideoPlayerLifecycleListener(VideoPlayerLifecycleListener lifecycleListener) {
        if (lifecycleListener == null) return;
        lifecycleListeners.remove(lifecycleListener);
    }

    private void notifyOnVideoStart() {
        for (VideoPlayerLifecycleListener listener : lifecycleListeners) {
            if (listener != null) {
                listener.onStart();
            }
        }
    }

    private void notifyOnVideoPause() {
        for (VideoPlayerLifecycleListener listener : lifecycleListeners) {
            if (listener != null) {
                listener.onPause();
            }
        }
    }

    private void notifyOnVideoStop() {
        for (VideoPlayerLifecycleListener listener : lifecycleListeners) {
            if (listener != null) {
                listener.onStop();
            }
        }
    }

    private OnPrepareFinishListener mOnPrepareFinishListener;

    public void setOnPrepareFinishListener(OnPrepareFinishListener mOnPrepareFinishListener) {
        this.mOnPrepareFinishListener = mOnPrepareFinishListener;
    }

    public interface OnPrepareFinishListener {
        void OnPrepareFinish();
    }

}
