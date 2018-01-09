package com.alphamovie.example;

import android.app.Activity;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.alphamovie.lib.Utils;
import com.alphamovie.lib.independent.AlphaMoviePlayer;
import com.alphamovie.lib.independent.VideoPlayer;

import java.io.File;

/**
 * Created by luotian on 2018/1/5.
 */

public class IndependentActivity extends Activity {

    public static final String FILENAME = "color1.mp4";
    public static final String FILENAMEBG = "base1.mp4";
    public static final String FILENAMEALPHA = "alpha1.mp4";

    private SurfaceView glSurfaceView;

    private VideoPlayer videoPlayer;

    private TextureView textureView;

    private AlphaMoviePlayer alphaMoviePlayer;

    private boolean isSplit = false;

    private boolean isVideoPlayerPrepared = false;
    private boolean isAlphaMovieViewPrepared = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFormat(PixelFormat.TRANSLUCENT);
        setContentView(R.layout.activity_inde);

        findViewById(R.id.play_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                play();
            }
        });

        findViewById(R.id.pause_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause();
            }
        });

        findViewById(R.id.stop_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop();
            }
        });

        String desPath = getFilesDir().getAbsolutePath() + File.separator;
        Utils.copyAssetFileToPath(this, FILENAME, desPath);
        Utils.copyAssetFileToPath(this, FILENAMEBG, desPath);
        Utils.copyAssetFileToPath(this, FILENAMEALPHA, desPath);

        glSurfaceView = (SurfaceView) findViewById(R.id.video_player);

        videoPlayer = new VideoPlayer(glSurfaceView);

        videoPlayer.init(FILENAMEBG);
        videoPlayer.setOnPrepareFinishListener(new VideoPlayer.OnPrepareFinishListener() {
            @Override
            public void OnPrepareFinish() {
                isVideoPlayerPrepared = true;
                if (isAlphaMovieViewPrepared) {
                    play();
                }
            }
        });

        textureView = (TextureView) findViewById(R.id.alpha_view);

        alphaMoviePlayer = new AlphaMoviePlayer();
        alphaMoviePlayer.init(textureView, videoPlayer);
        alphaMoviePlayer.setOnPrepareFinishListener(new AlphaMoviePlayer.OnPrepareFinishListener() {
            @Override
            public void OnPrepareFinish() {
                isAlphaMovieViewPrepared = true;
                if (isVideoPlayerPrepared) {
                    play();
                }
            }
        });
        alphaMoviePlayer.setVideoFromAssets(FILENAME, FILENAMEALPHA);


        findViewById(R.id.switch_layer_btn).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RelativeLayout.LayoutParams params= (RelativeLayout.LayoutParams) textureView.getLayoutParams();
                if (isSplit) {
                    params.addRule(RelativeLayout.BELOW, 0);
                    isSplit = false;
                } else {
                    params.addRule(RelativeLayout.BELOW, R.id.video_player);
                    isSplit = true;
                }
                textureView.setLayoutParams(params);
            }
        });

        findViewById(R.id.test).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(IndependentActivity.this, "click on test btn", Toast.LENGTH_SHORT).show();
            }
        });

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

    public void play() {
        videoPlayer.play();
//        textureView.start();
    }

    public void pause() {
        videoPlayer.pause();
//        textureView.pause();
    }

    public void stop() {
        videoPlayer.stop();
//        textureView.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        alphaMoviePlayer.release();
        alphaMoviePlayer = null;
    }
}
