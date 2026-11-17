package com.termux.app.file;

import android.app.Activity;
import android.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.termux.R;

import java.io.File;
import java.util.Locale;

/**
 * Modal dialog that shows the metadata of an APK file picked in the drawer and offers to install
 * it. The install button label and the status line reflect whether the package is already
 * installed and how its version compares to the one in the APK.
 */
public final class ApkInfoDialog {

    private ApkInfoDialog() {
    }

    /**
     * Parse {@code apkFile} and show its information dialog.
     *
     * <p>Only reading happens here; nothing is installed until the positive button is tapped.</p>
     */
    public static void show(@NonNull Activity activity, @NonNull File apkFile) {
        ApkInfo parsedInfo;
        try {
            parsedInfo = ApkInfo.parse(activity, apkFile);
        } catch (RuntimeException e) {
            parsedInfo = null;
        }
        if (parsedInfo == null) {
            Toast.makeText(activity, R.string.msg_apk_unreadable, Toast.LENGTH_SHORT).show();
            return;
        }
        final ApkInfo info = parsedInfo;

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_apk_info, null, false);
        ImageView iconView = content.findViewById(R.id.apk_info_icon);
        TextView labelView = content.findViewById(R.id.apk_info_label);
        TextView packageView = content.findViewById(R.id.apk_info_package);
        TextView statusView = content.findViewById(R.id.apk_info_status);
        TextView detailsView = content.findViewById(R.id.apk_info_details);

        labelView.setText(info.label);
        packageView.setText(info.packageName);
        if (info.icon != null) {
            iconView.setImageDrawable(info.icon);
        } else {
            iconView.setImageResource(R.drawable.ic_drawer_apk);
        }
        statusView.setText(buildStatusText(activity, info));
        detailsView.setText(buildDetailsText(activity, info));

        new AlertDialog.Builder(activity)
            .setTitle(R.string.title_apk_info)
            .setView(content)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(buildInstallActionLabel(activity, info),
                (dialog, which) -> ApkInstallUtils.installApk(activity, info.file))
            .show();
    }

    /** The version comparison between the APK and the installed package, as a short status line. */
    @NonNull
    private static CharSequence buildStatusText(@NonNull Activity activity, @NonNull ApkInfo info) {
        if (!info.installed) return activity.getString(R.string.apk_info_not_installed);

        String installedVersion = info.installedVersionName != null
            ? info.installedVersionName : String.valueOf(info.installedVersionCode);
        String apkVersion = info.versionName != null
            ? info.versionName : String.valueOf(info.versionCode);

        if (info.installedVersionCode > info.versionCode)
            return activity.getString(R.string.apk_info_downgrade_warning, installedVersion, apkVersion);
        if (info.installedVersionCode == info.versionCode)
            return activity.getString(R.string.apk_info_installed_same, installedVersion);
        if (info.versionCode > info.installedVersionCode)
            return activity.getString(R.string.apk_info_update_available, installedVersion, apkVersion);
        return activity.getString(R.string.apk_info_installed, installedVersion);
    }

    /** The label of the install button: install, update, reinstall or downgrade. */
    @NonNull
    private static CharSequence buildInstallActionLabel(@NonNull Activity activity,
                                                        @NonNull ApkInfo info) {
        if (!info.installed) return activity.getString(R.string.action_apk_install);
        if (info.versionCode > info.installedVersionCode)
            return activity.getString(R.string.action_apk_update);
        if (info.versionCode < info.installedVersionCode)
            return activity.getString(R.string.action_apk_downgrade);
        return activity.getString(R.string.action_apk_reinstall);
    }

    /** One line per field: package, version, size, SDK range and ABIs. */
    @NonNull
    private static CharSequence buildDetailsText(@NonNull Activity activity, @NonNull ApkInfo info) {
        String version = info.versionName != null
            ? info.versionName : String.valueOf(info.versionCode);
        String size = formatFileSize(info.fileSize);
        String abi = info.abis.isEmpty()
            ? activity.getString(R.string.apk_info_no_abi) : joinAbis(info.abis);

        if (info.minSdkVersion == 0 && info.targetSdkVersion == 0) {
            return activity.getString(R.string.apk_info_details_no_sdk,
                info.packageName, version, info.versionCode, size, abi);
        }
        return activity.getString(R.string.apk_info_details,
            info.packageName, version, info.versionCode, size,
            String.valueOf(info.minSdkVersion), String.valueOf(info.targetSdkVersion), abi);
    }

    @NonNull
    private static String joinAbis(@NonNull Iterable<String> abis) {
        StringBuilder builder = new StringBuilder();
        for (String abi : abis) {
            if (builder.length() > 0) builder.append(", ");
            builder.append(abi);
        }
        return builder.toString();
    }

    /** Human readable file size, matching the units used elsewhere in the app. */
    @NonNull
    public static String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        double value = size;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unitIndex = -1;
        while (value >= 1024 && unitIndex < units.length - 1) {
            value /= 1024;
            unitIndex++;
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex]);
    }
}
