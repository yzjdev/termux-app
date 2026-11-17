package com.termux.app.file;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Metadata of an APK file on disk, together with the state of the package it would install or
 * update.
 *
 * <p>The APK is only read through {@link PackageManager#getPackageArchiveInfo}, so simply showing
 * the information never installs or modifies anything.</p>
 */
public final class ApkInfo {

    /** The APK file the information was read from. */
    public final File file;
    public final String packageName;
    /** The application label, falling back to the file name when the APK declares none. */
    public final String label;
    @Nullable
    public final String versionName;
    public final long versionCode;
    /** Minimum/target SDK of the APK, or 0 when they cannot be read on this Android version. */
    public final int minSdkVersion;
    public final int targetSdkVersion;
    public final long fileSize;
    @Nullable
    public final Drawable icon;
    /** Native ABIs shipped by the APK (from its {@code lib/} directory), empty for pure Java APKs. */
    public final List<String> abis;

    public final boolean installed;
    @Nullable
    public final String installedVersionName;
    /** Version code of the installed package, or -1 when it is not installed. */
    public final long installedVersionCode;

    private ApkInfo(File file, String packageName, String label, @Nullable String versionName,
                    long versionCode, int minSdkVersion, int targetSdkVersion, long fileSize,
                    @Nullable Drawable icon, List<String> abis, boolean installed,
                    @Nullable String installedVersionName, long installedVersionCode) {
        this.file = file;
        this.packageName = packageName;
        this.label = label;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.minSdkVersion = minSdkVersion;
        this.targetSdkVersion = targetSdkVersion;
        this.fileSize = fileSize;
        this.icon = icon;
        this.abis = abis;
        this.installed = installed;
        this.installedVersionName = installedVersionName;
        this.installedVersionCode = installedVersionCode;
    }

    /**
     * Read the information of {@code apkFile}.
     *
     * @return The parsed information, or {@code null} when the file is missing or is not a valid APK.
     */
    @Nullable
    public static ApkInfo parse(@NonNull Context context, @NonNull File apkFile) {
        if (!apkFile.isFile()) return null;
        PackageManager packageManager = context.getPackageManager();

        PackageInfo archiveInfo = packageManager.getPackageArchiveInfo(
            apkFile.getAbsolutePath(), 0);
        if (archiveInfo == null || archiveInfo.packageName == null) return null;

        String label = null;
        Drawable icon = null;
        int minSdkVersion = 0;
        int targetSdkVersion = 0;
        ApplicationInfo applicationInfo = archiveInfo.applicationInfo;
        if (applicationInfo != null) {
            // Labels and icons of an archive are only loaded when the source dirs are set.
            applicationInfo.sourceDir = apkFile.getAbsolutePath();
            applicationInfo.publicSourceDir = apkFile.getAbsolutePath();
            CharSequence loadedLabel = applicationInfo.loadLabel(packageManager);
            if (loadedLabel != null) label = loadedLabel.toString();
            try {
                icon = applicationInfo.loadIcon(packageManager);
            } catch (RuntimeException e) {
                icon = null;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                minSdkVersion = applicationInfo.minSdkVersion;
                targetSdkVersion = applicationInfo.targetSdkVersion;
            }
        }
        if (label == null || label.isEmpty()) label = apkFile.getName();

        long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? archiveInfo.getLongVersionCode() : archiveInfo.versionCode;

        boolean installed = false;
        String installedVersionName = null;
        long installedVersionCode = -1;
        try {
            PackageInfo installedInfo = packageManager.getPackageInfo(archiveInfo.packageName, 0);
            installed = true;
            installedVersionName = installedInfo.versionName;
            installedVersionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? installedInfo.getLongVersionCode() : installedInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            // Not installed: the dialog then offers a plain install.
        }

        return new ApkInfo(apkFile, archiveInfo.packageName, label, archiveInfo.versionName,
            versionCode, minSdkVersion, targetSdkVersion, apkFile.length(), icon,
            readAbis(apkFile), installed, installedVersionName, installedVersionCode);
    }

    /** Collect the ABI directory names of the {@code lib/} entries, e.g. {@code arm64-v8a}. */
    private static List<String> readAbis(@NonNull File apkFile) {
        List<String> abis = new ArrayList<>();
        try (ZipFile zipFile = new ZipFile(apkFile)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (!name.startsWith("lib/")) continue;
                int end = name.indexOf('/', 4);
                if (end <= 4) continue;
                String abi = name.substring(4, end);
                if (!abis.contains(abi)) abis.add(abi);
            }
        } catch (IOException | RuntimeException e) {
            return Collections.emptyList();
        }
        Collections.sort(abis);
        return abis;
    }
}
