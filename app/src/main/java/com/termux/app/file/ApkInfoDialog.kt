package com.termux.app.file

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.widget.TextViewCompat
import com.termux.R
import java.io.File
import java.util.Locale

/**
 * Modal dialog that shows the metadata of an APK file picked in the drawer and offers to install
 * it. The install button label and the status line reflect whether the package is already
 * installed and how its version compares to the one in the APK.
 *
 * The dialog body is built in code instead of being inflated from a layout resource, so the app
 * module keeps no layout XML for it. Only reading happens here; nothing is installed until the
 * positive button is tapped.
 */
object ApkInfoDialog {

    /** Parse [apkFile] and show its information dialog. */
    @JvmStatic
    fun show(activity: Activity, apkFile: File) {
        val parsedInfo: ApkInfo? = try {
            ApkInfo.parse(activity, apkFile)
        } catch (e: RuntimeException) {
            null
        }
        if (parsedInfo == null) {
            Toast.makeText(activity, R.string.msg_apk_unreadable, Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.title_apk_info)
            .setView(buildContentView(activity, parsedInfo))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(buildInstallActionLabel(activity, parsedInfo)) { _, _ ->
                ApkInstallUtils.installApk(activity, parsedInfo.file)
            }
            .show()
    }

    /**
     * Build the dialog body: the APK icon, its label and package name, the version status line and
     * the detail lines. The spacing mirrors the layout the dialog used to be inflated from.
     */
    private fun buildContentView(activity: Activity, info: ApkInfo): LinearLayout {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Float): Int = (value * density).toInt()

        val container = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(12f), dp(20f), 0)
        }

        // Header: icon plus label and package name.
        val header = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageDrawable(
                info.icon ?: AppCompatResources.getDrawable(activity, R.drawable.ic_drawer_apk)
            )
        }
        header.addView(
            iconView,
            LinearLayout.LayoutParams(dp(48f), dp(48f)).apply { marginEnd = dp(14f) }
        )

        val textColumn = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val labelView = TextView(activity).apply {
            setTextColor(themeColor(activity, android.R.attr.textColorPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = info.label
        }
        val packageView = TextView(activity).apply {
            setTextColor(themeColor(activity, android.R.attr.textColorSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.MIDDLE
            text = info.packageName
        }
        textColumn.addView(
            labelView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        textColumn.addView(
            packageView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        header.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        container.addView(
            header,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // Version comparison status.
        val statusView = TextView(activity).apply {
            setTextColor(themeColor(activity, android.R.attr.textColorPrimary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            text = buildStatusText(activity, info)
        }
        container.addView(
            statusView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = dp(10f) }
        )

        // Detail lines: package, version, size, SDK range and ABIs.
        val detailsView = TextView(activity).apply {
            setTextColor(themeColor(activity, android.R.attr.textColorSecondary))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            TextViewCompat.setLineHeight(this, dp(19f))
            text = buildDetailsText(activity, info)
        }
        container.addView(
            detailsView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply {
                    topMargin = dp(6f)
                    bottomMargin = dp(4f)
                }
        )

        return container
    }

    /** Resolve a colour from the current theme, falling back to the default text colour. */
    private fun themeColor(context: Context, attr: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) value.data else 0xFF000000.toInt()
    }

    /** The version comparison between the APK and the installed package, as a short status line. */
    private fun buildStatusText(activity: Activity, info: ApkInfo): String {
        if (!info.installed) return activity.getString(R.string.apk_info_not_installed)

        val installedVersion = info.installedVersionName ?: info.installedVersionCode.toString()
        val apkVersion = info.versionName ?: info.versionCode.toString()

        return when {
            info.installedVersionCode > info.versionCode ->
                activity.getString(R.string.apk_info_downgrade_warning, installedVersion, apkVersion)

            info.installedVersionCode == info.versionCode ->
                activity.getString(R.string.apk_info_installed_same, installedVersion)

            info.versionCode > info.installedVersionCode ->
                activity.getString(R.string.apk_info_update_available, installedVersion, apkVersion)

            else -> activity.getString(R.string.apk_info_installed, installedVersion)
        }
    }

    /** The label of the install button: install, update, reinstall or downgrade. */
    private fun buildInstallActionLabel(activity: Activity, info: ApkInfo): CharSequence {
        if (!info.installed) return activity.getString(R.string.action_apk_install)
        if (info.versionCode > info.installedVersionCode) return activity.getString(R.string.action_apk_update)
        if (info.versionCode < info.installedVersionCode) return activity.getString(R.string.action_apk_downgrade)
        return activity.getString(R.string.action_apk_reinstall)
    }

    /** One line per field: package, version, size, SDK range and ABIs. */
    private fun buildDetailsText(activity: Activity, info: ApkInfo): String {
        val version = info.versionName ?: info.versionCode.toString()
        val size = formatFileSize(info.fileSize)
        val abi = if (info.abis.isEmpty()) activity.getString(R.string.apk_info_no_abi)
        else info.abis.joinToString(", ")

        if (info.minSdkVersion == 0 && info.targetSdkVersion == 0) {
            return activity.getString(
                R.string.apk_info_details_no_sdk,
                info.packageName, version, info.versionCode, size, abi
            )
        }
        return activity.getString(
            R.string.apk_info_details,
            info.packageName, version, info.versionCode, size,
            info.minSdkVersion.toString(), info.targetSdkVersion.toString(), abi
        )
    }

    /** Human readable file size, matching the units used elsewhere in the app. */
    @JvmStatic
    fun formatFileSize(size: Long): String {
        if (size < 1024) return "$size B"
        var value = size.toDouble()
        val units = arrayOf("KB", "MB", "GB", "TB")
        var unitIndex = -1
        while (value >= 1024 && unitIndex < units.size - 1) {
            value /= 1024
            unitIndex++
        }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unitIndex])
    }
}
