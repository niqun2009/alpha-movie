package com.alphamovie.lib.independent;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.graphics.SurfaceTexture;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import com.alphamovie.lib.MediaPlayerBlob;
import com.alphamovie.lib.Utils;

import java.io.IOException;

/**
 * Created by luotian on 2018/1/8.
 */

public class AlphaMoviePlayer {

    private static final int GL_CONTEXT_VERSION = 2;

    private static final int NOT_DEFINED = -1;
    private static final int NOT_DEFINED_COLOR = 0;

    private static final String TAG = "VideoSurfaceView";

    private static final float VIEW_ASPECT_RATIO = 4f / 3f;
    private float videoAspectRatio = VIEW_ASPECT_RATIO;

//    VideoRenderer renderer;

    private boolean isSurfaceCreated = false;
    private boolean isDataSourceSet = false;
    private boolean isMediaPlayerBlobPrepared = false;
    private boolean isMediaPlayerBlobAlphaPrepared = false;

    private MediaPlayerBlob mMediaPlayerBlob;
    private MediaPlayerBlob mMediaPlayerBlobAlpha;

    private VideoPlayer mVideoPlayer;

    private static volatile boolean sReleaseInCallback = true;

    private TextureView mTextureView;
    private AlphaMovieTextureRender mRenderer;

    public void init(TextureView textureView, VideoPlayer videoPlayer) {
        this.mVideoPlayer = videoPlayer;

        mTextureView = textureView;
        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mRenderer = new AlphaMovieTextureRender(mTextureView.getContext(), surface, width, height);
                mRenderer.setOnSurfacePrepareListener(new AlphaMovieTextureRender.OnSurfacePrepareListener() {
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

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mRenderer.onPause();
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });

        initMediaPlayer();

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

//    private void addOnSurfacePrepareListener() {
//        if (renderer != null) {
//            renderer.setOnSurfacePrepareListener(new VideoRenderer.OnSurfacePrepareListener() {
//                @Override
//                public void surfacePrepared(Surface surface, Surface alphaSurface) {
//                    isSurfaceCreated = true;
//
//                    mMediaPlayerBlob.getMediaPlayer().setSurface(surface);
//                    surface.release();
//
//                    mMediaPlayerBlobAlpha.getMediaPlayer().setSurface(alphaSurface);
//                    alphaSurface.release();
//
//                    if (isDataSourceSet) {
//                        prepareAndStartMediaPlayer();
//                    }
//                }
//            });
//        }
//    }

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

        ViewGroup.LayoutParams layoutParams = mTextureView.getLayoutParams();
        layoutParams.width = Utils.getScreenWidth((Activity) mTextureView.getContext());
        layoutParams.height = (int) (((float)layoutParams.width) / videoAspectRatio);
        mTextureView.setLayoutParams(layoutParams);
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
            AssetFileDescriptor assetFileDescriptor = mTextureView.getContext().getAssets().openFd(assetsFileName);
            mMediaPlayerBlob.getMediaPlayer().setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            MediaMetadataRetriever retrieverBg = new MediaMetadataRetriever();
            retrieverBg.setDataSource(assetFileDescriptor.getFileDescriptor(), assetFileDescriptor.getStartOffset(), assetFileDescriptor.getLength());

            AssetFileDescriptor assetFileDescriptorAlpha = mTextureView.getContext().getAssets().openFd(assetsFileNameAlpha);
            mMediaPlayerBlobAlpha.getMediaPlayer().setDataSource(assetFileDescriptorAlpha.getFileDescriptor(), assetFileDescriptorAlpha.getStartOffset(), assetFileDescriptorAlpha.getLength());

            onDataSourceSet(retrieverBg);

        } catch (IOException e) {
            Log.e(TAG, e.getMessage(), e);
        }
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
//        mRenderer.halt();
        mRenderer.onPause();
        mRenderer = null;
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
