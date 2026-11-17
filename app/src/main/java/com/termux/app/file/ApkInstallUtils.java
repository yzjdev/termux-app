package com.termux.app.file;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.termux.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

/**
 * Hands an APK file over to the system package installer.
 *
 * <p>The installer cannot read arbitrary paths (and {@code file://} URIs are rejected on
 * Android 7+), so the APK is first copied into the app's own cache directory and shared through a
 * {@link FileProvider} with a temporary read grant.</p>
 */
public final class ApkInstallUtils {

    private static final String APK_FILE_EXTENSION = ".apk";
    private static final String APK_MIME_TYPE = "application/vnd.android.package-archive";
    /** Suffix of the {@code FileProvider} authority declared in the manifest. */
    private static final String FILE_PROVIDER_AUTHORITY_SUFFIX = ".apkprovider";
    /** Sub directory of the cache dir the APK is copied to; must match {@code apk_file_paths.xml}. */
    private static final String CACHE_DIR_NAME = "apk";

    private ApkInstallUtils() {
    }

    /** Whether {@code file} has the APK extension and can be read as a regular file. */
    public static boolean isApkFile(@NonNull File file) {
        return file.isFile()
            && file.getName().toLowerCase(Locale.ROOT).endsWith(APK_FILE_EXTENSION);
    }

    /**
     * Ask the system to install {@code apkFile}, showing a dialog when the "install unknown apps"
     * permission has not been granted yet. The copy and the hand-over happen off the main thread
     * and back on it, so a large APK does not block the drawer.
     */
    public static void installApk(@NonNull Activity activity, @NonNull File apkFile) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            && !activity.getPackageManager().canRequestPackageInstalls()) {
            showInstallPermissionDialog(activity);
            return;
        }

        final Context appContext = activity.getApplicationContext();
        final String apkPath = apkFile.getAbsolutePath();
        Toast.makeText(activity, R.string.msg_apk_preparing_install, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            File copiedApk;
            try {
                copiedApk = copyToCache(appContext, new File(apkPath));
            } catch (IOException | RuntimeException e) {
                copiedApk = null;
            }
            final File sharedApk = copiedApk;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing()) return;
                if (sharedApk == null) {
                    Toast.makeText(activity, R.string.msg_apk_prepare_install_failed,
                        Toast.LENGTH_SHORT).show();
                } else {
                    startInstaller(activity, sharedApk);
                }
            });
        }, "termux-apk-install").start();
    }

    /** Copy {@code apkFile} into the shared cache directory, dropping stale copies of other APKs. */
    private static File copyToCache(@NonNull Context context, @NonNull File apkFile)
        throws IOException {
        File cacheDir = new File(context.getCacheDir(), CACHE_DIR_NAME);
        if (!cacheDir.isDirectory() && !cacheDir.mkdirs())
            throw new IOException("Failed to create directory " + cacheDir);

        File target = new File(cacheDir, apkFile.getName());
        File[] staleFiles = cacheDir.listFiles();
        if (staleFiles != null) {
            for (File staleFile : staleFiles) {
                if (!staleFile.equals(target)) staleFile.delete();
            }
        }

        try (InputStream in = new FileInputStream(apkFile);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
        }
        return target;
    }

    private static void startInstaller(@NonNull Activity activity, @NonNull File apkFile) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(activity,
                activity.getPackageName() + FILE_PROVIDER_AUTHORITY_SUFFIX, apkFile);
        } catch (IllegalArgumentException e) {
            Toast.makeText(activity, R.string.msg_apk_prepare_install_failed,
                Toast.LENGTH_SHORT).show();
            return;
        }

        Intent viewIntent = new Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(viewIntent);
            return;
        } catch (ActivityNotFoundException e) {
            // Some devices only answer the dedicated install action.
        }

        Intent installIntent = new Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            activity.startActivity(installIntent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity, R.string.msg_apk_no_installer, Toast.LENGTH_SHORT).show();
        }
    }

    /** Explain why the install cannot start and offer to open the "install unknown apps" setting. */
    private static void showInstallPermissionDialog(@NonNull Activity activity) {
        new AlertDialog.Builder(activity)
            .setTitle(R.string.title_apk_install_permission)
            .setMessage(activity.getString(R.string.msg_apk_install_permission_required,
                activity.getString(R.string.application_name)))
            .setPositiveButton(R.string.action_open_install_settings, (dialog, which) -> {
                Intent intent = new Intent(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    ? Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
                    : Settings.ACTION_SECURITY_SETTINGS);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                try {
                    activity.startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    try {
                        activity.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
                    } catch (ActivityNotFoundException ignored) {
                        Toast.makeText(activity, R.string.msg_apk_no_installer,
                            Toast.LENGTH_SHORT).show();
                    }
                }
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }
}
