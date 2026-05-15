package org.petero.droidfish;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;

/**
 * Centralized provider for all DroidFish storage paths.
 * On API 30+ uses app-specific external storage (no permission required).
 * On API 29 and below uses legacy external storage for backward compatibility.
 */
public class StorageProvider {

    private static final String APP_DIR = "DroidFish";

    private StorageProvider() {}

    public static File getBaseDir() {
        if (Build.VERSION.SDK_INT >= 30) {
            Context ctx = DroidFishApp.getContext();
            return new File(ctx.getExternalFilesDir(null), APP_DIR);
        }
        return new File(Environment.getExternalStorageDirectory(), APP_DIR);
    }

    public static File getBookDir() {
        return new File(getBaseDir(), "book");
    }

    public static File getPgnDir() {
        return new File(getBaseDir(), "pgn");
    }

    public static File getFenDir() {
        return new File(getBaseDir(), "epd");
    }

    public static File getEngineDir() {
        return new File(getBaseDir(), "uci");
    }

    public static File getEngineLogDir() {
        return new File(getBaseDir(), "uci/logs");
    }

    public static File getGtbDefaultDir() {
        return new File(getBaseDir(), "gtb");
    }

    public static File getRtbDefaultDir() {
        return new File(getBaseDir(), "rtb");
    }

    /**
     * Returns the legacy base directory path (pre-Scoped Storage).
     * Used by the migration helper to locate old user data.
     */
    public static File getLegacyBaseDir() {
        return new File(Environment.getExternalStorageDirectory(), APP_DIR);
    }

    /** Ensure all required directories exist. */
    public static void createDirectories() {
        getBookDir().mkdirs();
        getPgnDir().mkdirs();
        getFenDir().mkdirs();
        getEngineDir().mkdirs();
        getEngineLogDir().mkdirs();
        getGtbDefaultDir().mkdirs();
        getRtbDefaultDir().mkdirs();
    }
}
