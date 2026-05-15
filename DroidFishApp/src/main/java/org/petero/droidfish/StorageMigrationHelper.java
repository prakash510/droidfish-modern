package org.petero.droidfish;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

/**
 * Handles one-time migration of DroidFish user data from legacy external storage
 * to app-specific external storage when upgrading to Scoped Storage (API 30+).
 */
public class StorageMigrationHelper {

    private static final String TAG = "StorageMigration";
    private static final String PREF_MIGRATION_DONE = "storageMigrationDone";
    private static final long MAX_AUTO_MIGRATE_BYTES = 2L * 1024 * 1024 * 1024; // 2 GB

    private StorageMigrationHelper() {}

    /** Run migration if needed. Returns true if migration was performed or not needed. */
    public static boolean migrateIfNeeded(Context context) {
        if (Build.VERSION.SDK_INT < 30) {
            return true;
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (prefs.getBoolean(PREF_MIGRATION_DONE, false)) {
            return true;
        }

        File legacyDir = StorageProvider.getLegacyBaseDir();
        if (!legacyDir.exists() || !legacyDir.isDirectory()) {
            prefs.edit().putBoolean(PREF_MIGRATION_DONE, true).apply();
            return true;
        }

        File newDir = StorageProvider.getBaseDir();
        newDir.mkdirs();

        boolean success = true;
        String[] subDirs = {"book", "pgn", "epd", "uci"};
        for (String sub : subDirs) {
            File src = new File(legacyDir, sub);
            File dst = new File(newDir, sub);
            if (src.exists() && src.isDirectory()) {
                if (dirSize(src) <= MAX_AUTO_MIGRATE_BYTES) {
                    if (!copyDirectory(src, dst)) {
                        success = false;
                        Log.e(TAG, "Failed to migrate: " + sub);
                    }
                } else {
                    Log.w(TAG, "Skipping large directory: " + sub);
                }
            }
        }

        // Tablebase dirs — only migrate if small, otherwise user should re-point via settings
        String[] tbDirs = {"gtb", "rtb"};
        for (String sub : tbDirs) {
            File src = new File(legacyDir, sub);
            File dst = new File(newDir, sub);
            if (src.exists() && src.isDirectory()) {
                long size = dirSize(src);
                if (size > 0 && size <= MAX_AUTO_MIGRATE_BYTES) {
                    if (!copyDirectory(src, dst)) {
                        Log.e(TAG, "Failed to migrate tablebase: " + sub);
                    }
                } else if (size > MAX_AUTO_MIGRATE_BYTES) {
                    Log.w(TAG, "Skipping large tablebase dir (" + (size / (1024*1024)) + " MB): " + sub);
                }
            }
        }

        if (success) {
            migratePreferencePaths(context);
            prefs.edit().putBoolean(PREF_MIGRATION_DONE, true).apply();
        }

        return success;
    }

    /** Rewrite stored preference paths from legacy location to new location. */
    private static void migratePreferencePaths(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        String legacyBase = StorageProvider.getLegacyBaseDir().getAbsolutePath();
        String newBase = StorageProvider.getBaseDir().getAbsolutePath();

        String[] pathKeys = {"currentPGNFile", "currentFENFile", "bookFile", "gtbPath", "rtbPath"};
        for (String key : pathKeys) {
            String value = prefs.getString(key, null);
            if (value != null && value.startsWith(legacyBase)) {
                String migrated = newBase + value.substring(legacyBase.length());
                editor.putString(key, migrated);
                Log.i(TAG, "Migrated pref " + key + ": " + value + " -> " + migrated);
            }
        }
        editor.apply();
    }

    private static boolean copyDirectory(File src, File dst) {
        if (!dst.exists()) {
            dst.mkdirs();
        }
        File[] files = src.listFiles();
        if (files == null) {
            return true;
        }
        boolean success = true;
        for (File file : files) {
            File dstFile = new File(dst, file.getName());
            if (file.isDirectory()) {
                if (!copyDirectory(file, dstFile)) {
                    success = false;
                }
            } else {
                if (!dstFile.exists() || file.lastModified() > dstFile.lastModified()) {
                    if (!copyFile(file, dstFile)) {
                        success = false;
                    }
                }
            }
        }
        return success;
    }

    private static boolean copyFile(File src, File dst) {
        try (FileInputStream fis = new FileInputStream(src);
             FileChannel inChannel = fis.getChannel();
             FileOutputStream fos = new FileOutputStream(dst);
             FileChannel outChannel = fos.getChannel()) {
            long size = inChannel.size();
            long transferred = outChannel.transferFrom(inChannel, 0, size);
            if (transferred < size) {
                return false;
            }
            dst.setLastModified(src.lastModified());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to copy " + src.getName() + ": " + e.getMessage());
            return false;
        }
    }

    private static long dirSize(File dir) {
        long size = 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        for (File file : files) {
            if (file.isDirectory()) {
                size += dirSize(file);
            } else {
                size += file.length();
            }
        }
        return size;
    }
}
