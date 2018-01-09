package com.alphamovie.lib;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.DisplayMetrics;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Created by luotian on 2017/12/7.
 */

public class Utils {

    public static void copyAssetFileToPath(Context context, String filename, String desPath) {
        AssetManager assetManager = context.getAssets();

        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(filename);
            String newFileName = desPath + filename;
            File desFile = new File(desPath);
            desFile.mkdirs();

            if (new File(newFileName).exists()) {
                return;
            }

            out = new FileOutputStream(newFileName);
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
        } catch (Throwable e) {
            Log.e("", e.getMessage());
        }
    }

    public static int getScreenWidth(Activity activity) {
        DisplayMetrics displayMetrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        return displayMetrics.widthPixels;
    }

}
