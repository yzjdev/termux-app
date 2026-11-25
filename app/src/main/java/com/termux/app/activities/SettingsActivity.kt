package com.termux.app.activities

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.termux.R
import com.termux.app.models.UserAction
import com.termux.app.ui.theme.TermuxTheme
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.activities.ReportActivity
import com.termux.shared.android.AndroidUtils
import com.termux.shared.android.PackageUtils
import com.termux.shared.file.FileUtils
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.models.ReportInfo
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.settings.preferences.TermuxAPIAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxFloatAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxTaskerAppSharedPreferences
import com.termux.shared.termux.settings.preferences.TermuxWidgetAppSharedPreferences
import com.termux.shared.theme.NightMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Settings entry. Every toggle/list still reads and writes through the exact same
 * [TermuxAppSharedPreferences] / plugin preference classes the terminal reads, so behaviour stays
 * identical to the former AndroidX Preference fragments — only the UI is Compose.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true)
        setContent {
            TermuxTheme {
                SettingsScreen(onBack = { onBackPressed() })
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}

/** A single row of the settings list. */
private sealed interface SettingItem {
    data class Section(val title: String) : SettingItem
    data class Nav(val title: String, val summary: String?, val visible: Boolean, val onClick: () -> Unit) : SettingItem
    data class Switch(
        val title: String,
        val summaryOn: String,
        val summaryOff: String,
        var checked: Boolean,
        val onChange: (Boolean) -> Unit,
    ) : SettingItem
    data class List(
        val title: String,
        val entries: Array<String>,
        val entryValues: Array<String>,
        var selected: String,
        val onChange: (String) -> Unit,
    ) : SettingItem
}

/** Which sub-screen is currently displayed. */
private enum class SettingsPage {
    ROOT, TERMUX, TERMUX_DEBUGGING, TERMUX_TERMINAL_IO, TERMUX_TERMINAL_VIEW,
    TERMUX_API, TERMUX_API_DEBUGGING, TERMUX_FLOAT, TERMUX_FLOAT_DEBUGGING,
    TERMUX_TASKER, TERMUX_TASKER_DEBUGGING, TERMUX_WIDGET, TERMUX_WIDGET_DEBUGGING,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var page by remember { mutableStateOf(SettingsPage.ROOT) }
    var listDialog by remember { mutableStateOf<SettingItem.List?>(null) }

    // Build the items for the current page. Every read happens on each composition so values
    // reflect the latest SharedPreferences state.
    val items = remember(page) { buildItems(context, page) { page = it } }

    val title = when (page) {
        SettingsPage.ROOT -> stringResource(R.string.title_activity_termux_settings)
        SettingsPage.TERMUX, SettingsPage.TERMUX_DEBUGGING, SettingsPage.TERMUX_TERMINAL_IO,
        SettingsPage.TERMUX_TERMINAL_VIEW -> stringResource(R.string.termux_preferences_title)
        SettingsPage.TERMUX_API, SettingsPage.TERMUX_API_DEBUGGING -> stringResource(R.string.termux_api_preferences_title)
        SettingsPage.TERMUX_FLOAT, SettingsPage.TERMUX_FLOAT_DEBUGGING -> stringResource(R.string.termux_float_preferences_title)
        SettingsPage.TERMUX_TASKER, SettingsPage.TERMUX_TASKER_DEBUGGING -> stringResource(R.string.termux_tasker_preferences_title)
        SettingsPage.TERMUX_WIDGET, SettingsPage.TERMUX_WIDGET_DEBUGGING -> stringResource(R.string.termux_widget_preferences_title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TextButton(onClick = { if (page == SettingsPage.ROOT) onBack() else page = parentOf(page) }) {
                        Text("‹", style = MaterialTheme.typography.headlineMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            items(items) { item ->
                when (item) {
                    is SettingItem.Section -> SectionRow(item.title)
                    is SettingItem.Nav -> if (item.visible) NavRow(item)
                    is SettingItem.Switch -> SwitchRow(item, onChange = {
                        item.checked = it
                        item.onChange(it)
                    })
                    is SettingItem.List -> ListRow(item, onClick = { listDialog = item })
                }
            }
        }
    }

    listDialog?.let { dialog ->
        val currentIndex = dialog.entryValues.indexOf(dialog.selected).coerceAtLeast(0)
        AlertDialog(
            onDismissRequest = { listDialog = null },
            title = { Text(dialog.title) },
            text = {
                Column {
                    dialog.entries.forEachIndexed { index, label ->
                        val select = {
                            val value = dialog.entryValues[index]
                            dialog.onChange(value)
                            listDialog = null
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = select)
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = index == currentIndex, onClick = select)
                            Spacer(Modifier.width(8.dp))
                            Text(label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { listDialog = null }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SectionRow(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun NavRow(item: SettingItem.Nav) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge)
        item.summary?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider()
}

@Composable
private fun SwitchRow(item: SettingItem.Switch, onChange: (Boolean) -> Unit) {
    var checked by remember { mutableStateOf(item.checked) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { checked = !checked; onChange(checked) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                if (checked) item.summaryOn else item.summaryOff,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = { checked = it; onChange(it) })
    }
}

@Composable
private fun ListRow(item: SettingItem.List, onClick: () -> Unit) {
    val selectedLabel = item.entryValues.indexOf(item.selected).let { idx ->
        if (idx in item.entries.indices) item.entries[idx] else item.selected
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(item.title, style = MaterialTheme.typography.bodyLarge)
        Text(selectedLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

private fun parentOf(page: SettingsPage): SettingsPage = when (page) {
    SettingsPage.TERMUX_DEBUGGING, SettingsPage.TERMUX_TERMINAL_IO, SettingsPage.TERMUX_TERMINAL_VIEW -> SettingsPage.TERMUX
    SettingsPage.TERMUX_API_DEBUGGING -> SettingsPage.TERMUX_API
    SettingsPage.TERMUX_FLOAT_DEBUGGING -> SettingsPage.TERMUX_FLOAT
    SettingsPage.TERMUX_TASKER_DEBUGGING -> SettingsPage.TERMUX_TASKER
    SettingsPage.TERMUX_WIDGET_DEBUGGING -> SettingsPage.TERMUX_WIDGET
    else -> SettingsPage.ROOT
}

/** Resolve the plugin preference visibility exactly like the original root fragment. */
private fun pluginInstalled(context: Context, which: String): Boolean = try {
    when (which) {
        "api" -> TermuxAPIAppSharedPreferences.build(context, false) != null
        "float" -> TermuxFloatAppSharedPreferences.build(context, false) != null
        "tasker" -> TermuxTaskerAppSharedPreferences.build(context, false) != null
        "widget" -> TermuxWidgetAppSharedPreferences.build(context, false) != null
        else -> false
    }
} catch (e: Exception) {
    false
}

/** Whether the donate entry should be shown (hidden for Google Play builds). */
private fun donateVisible(context: Context): Boolean {
    val digest = PackageUtils.getSigningCertificateSHA256DigestForPackage(context)
        ?: return true
    val release = TermuxUtils.getAPKRelease(digest)
    return !(release == null || release == TermuxConstants.APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST)
}

/** Build the current page's items from the shared preference classes. */
private fun buildItems(context: Context, page: SettingsPage, navigate: (SettingsPage) -> Unit): List<SettingItem> {
    val items = mutableListOf<SettingItem>()
    when (page) {
        SettingsPage.ROOT -> {
            items += SettingItem.Nav(
                context.getString(R.string.termux_preferences_title),
                context.getString(R.string.termux_preferences_summary), true,
            ) { navigate(SettingsPage.TERMUX) }
            items += SettingItem.Nav(
                context.getString(R.string.termux_api_preferences_title),
                context.getString(R.string.termux_api_preferences_summary),
                pluginInstalled(context, "api"),
            ) { navigate(SettingsPage.TERMUX_API) }
            items += SettingItem.Nav(
                context.getString(R.string.termux_float_preferences_title),
                context.getString(R.string.termux_float_preferences_summary),
                pluginInstalled(context, "float"),
            ) { navigate(SettingsPage.TERMUX_FLOAT) }
            items += SettingItem.Nav(
                context.getString(R.string.termux_tasker_preferences_title),
                context.getString(R.string.termux_tasker_preferences_summary),
                pluginInstalled(context, "tasker"),
            ) { navigate(SettingsPage.TERMUX_TASKER) }
            items += SettingItem.Nav(
                context.getString(R.string.termux_widget_preferences_title),
                context.getString(R.string.termux_widget_preferences_summary),
                pluginInstalled(context, "widget"),
            ) { navigate(SettingsPage.TERMUX_WIDGET) }
            items += SettingItem.Nav(
                context.getString(R.string.atomcode_models_preferences_title),
                context.getString(R.string.atomcode_models_preferences_summary), true,
            ) { context.startActivity(Intent(context, AtomCodeModelsActivity::class.java)) }
            items += SettingItem.Nav(context.getString(R.string.about_preference_title), null, true) {
                showAbout(context)
            }
            items += SettingItem.Nav(context.getString(R.string.donate_preference_title), null, donateVisible(context)) {
                ShareUtils.openUrl(context, TermuxConstants.TERMUX_DONATE_URL)
            }
        }

        SettingsPage.TERMUX -> {
            items += SettingItem.Nav(
                context.getString(R.string.termux_debugging_preferences_title),
                context.getString(R.string.termux_debugging_preferences_summary), true,
            ) { navigate(SettingsPage.TERMUX_DEBUGGING) }
            items += SettingItem.Nav(
                context.getString(R.string.termux_terminal_io_preferences_title),
                context.getString(R.string.termux_terminal_io_preferences_summary), true,
            ) { navigate(SettingsPage.TERMUX_TERMINAL_IO) }
            items += SettingItem.Nav(
                context.getString(R.string.termux_terminal_view_preferences_title),
                context.getString(R.string.termux_terminal_view_preferences_summary), true,
            ) { navigate(SettingsPage.TERMUX_TERMINAL_VIEW) }
        }

        SettingsPage.TERMUX_DEBUGGING -> {
            items += SettingItem.Section(context.getString(R.string.termux_logging_header))
            val prefs = TermuxAppSharedPreferences.build(context, true)
            addLogLevel(context, items, prefs?.getLogLevel() ?: Logger.DEFAULT_LOG_LEVEL) { value ->
                prefs?.setLogLevel(context, value.toIntOrNull() ?: Logger.DEFAULT_LOG_LEVEL)
            }
            items += switchItem(
                context, R.string.termux_terminal_view_key_logging_enabled_title,
                R.string.termux_terminal_view_key_logging_enabled_on,
                R.string.termux_terminal_view_key_logging_enabled_off,
                prefs?.isTerminalViewKeyLoggingEnabled() ?: false,
            ) { prefs?.setTerminalViewKeyLoggingEnabled(it) }
            items += switchItem(
                context, R.string.termux_plugin_error_notifications_enabled_title,
                R.string.termux_plugin_error_notifications_enabled_on,
                R.string.termux_plugin_error_notifications_enabled_off,
                prefs?.arePluginErrorNotificationsEnabled(false) ?: true,
            ) { prefs?.setPluginErrorNotificationsEnabled(it) }
            items += switchItem(
                context, R.string.termux_crash_report_notifications_enabled_title,
                R.string.termux_crash_report_notifications_enabled_on,
                R.string.termux_crash_report_notifications_enabled_off,
                prefs?.areCrashReportNotificationsEnabled(false) ?: true,
            ) { prefs?.setCrashReportNotificationsEnabled(it) }
        }

        SettingsPage.TERMUX_TERMINAL_IO -> {
            items += SettingItem.Section(context.getString(R.string.termux_keyboard_header))
            val prefs = TermuxAppSharedPreferences.build(context, true)
            items += switchItem(
                context, R.string.termux_soft_keyboard_enabled_title,
                R.string.termux_soft_keyboard_enabled_on,
                R.string.termux_soft_keyboard_enabled_off,
                prefs?.isSoftKeyboardEnabled() ?: true,
            ) { prefs?.setSoftKeyboardEnabled(it) }
            items += switchItem(
                context, R.string.termux_soft_keyboard_enabled_only_if_no_hardware_title,
                R.string.termux_soft_keyboard_enabled_only_if_no_hardware_on,
                R.string.termux_soft_keyboard_enabled_only_if_no_hardware_off,
                prefs?.isSoftKeyboardEnabledOnlyIfNoHardware() ?: true,
            ) { prefs?.setSoftKeyboardEnabledOnlyIfNoHardware(it) }
        }

        SettingsPage.TERMUX_TERMINAL_VIEW -> {
            items += SettingItem.Section(context.getString(R.string.termux_terminal_view_view_header))
            val prefs = TermuxAppSharedPreferences.build(context, true)
            items += switchItem(
                context, R.string.termux_terminal_view_terminal_margin_adjustment_title,
                R.string.termux_terminal_view_terminal_margin_adjustment_on,
                R.string.termux_terminal_view_terminal_margin_adjustment_off,
                prefs?.isTerminalMarginAdjustmentEnabled() ?: false,
            ) { prefs?.setTerminalMarginAdjustment(it) }
        }

        SettingsPage.TERMUX_API -> pluginDebugNav(context, items, R.string.termux_api_preferences_title) { navigate(SettingsPage.TERMUX_API_DEBUGGING) }
        SettingsPage.TERMUX_FLOAT -> pluginDebugNav(context, items, R.string.termux_float_preferences_title) { navigate(SettingsPage.TERMUX_FLOAT_DEBUGGING) }
        SettingsPage.TERMUX_TASKER -> pluginDebugNav(context, items, R.string.termux_tasker_preferences_title) { navigate(SettingsPage.TERMUX_TASKER_DEBUGGING) }
        SettingsPage.TERMUX_WIDGET -> pluginDebugNav(context, items, R.string.termux_widget_preferences_title) { navigate(SettingsPage.TERMUX_WIDGET_DEBUGGING) }

        SettingsPage.TERMUX_API_DEBUGGING -> {
            items += SettingItem.Section(context.getString(R.string.termux_logging_header))
            val prefs = TermuxAPIAppSharedPreferences.build(context, true)
            addLogLevel(context, items, prefs?.getLogLevel(true) ?: Logger.DEFAULT_LOG_LEVEL) { value ->
                prefs?.setLogLevel(context, value.toIntOrNull() ?: Logger.DEFAULT_LOG_LEVEL, true)
            }
        }
        SettingsPage.TERMUX_FLOAT_DEBUGGING -> {
            items += SettingItem.Section(context.getString(R.string.termux_logging_header))
            val prefs = TermuxFloatAppSharedPreferences.build(context, true)
            addLogLevel(context, items, prefs?.getLogLevel(true) ?: Logger.DEFAULT_LOG_LEVEL) { value ->
                prefs?.setLogLevel(context, value.toIntOrNull() ?: Logger.DEFAULT_LOG_LEVEL, true)
            }
            items += switchItem(
                context, R.string.termux_terminal_view_key_logging_enabled_title,
                R.string.termux_terminal_view_key_logging_enabled_on,
                R.string.termux_terminal_view_key_logging_enabled_off,
                prefs?.isTerminalViewKeyLoggingEnabled(true) ?: false,
            ) { prefs?.setTerminalViewKeyLoggingEnabled(it, true) }
        }
        SettingsPage.TERMUX_TASKER_DEBUGGING -> {
            items += SettingItem.Section(context.getString(R.string.termux_logging_header))
            val prefs = TermuxTaskerAppSharedPreferences.build(context, true)
            addLogLevel(context, items, prefs?.getLogLevel(true) ?: Logger.DEFAULT_LOG_LEVEL) { value ->
                prefs?.setLogLevel(context, value.toIntOrNull() ?: Logger.DEFAULT_LOG_LEVEL, true)
            }
        }
        SettingsPage.TERMUX_WIDGET_DEBUGGING -> {
            items += SettingItem.Section(context.getString(R.string.termux_logging_header))
            val prefs = TermuxWidgetAppSharedPreferences.build(context, true)
            addLogLevel(context, items, prefs?.getLogLevel(true) ?: Logger.DEFAULT_LOG_LEVEL) { value ->
                prefs?.setLogLevel(context, value.toIntOrNull() ?: Logger.DEFAULT_LOG_LEVEL, true)
            }
        }
    }
    return items
}

private fun pluginDebugNav(context: Context, items: MutableList<SettingItem>, titleRes: Int, onClick: () -> Unit) {
    items += SettingItem.Nav(
        context.getString(R.string.termux_debugging_preferences_title),
        context.getString(R.string.termux_debugging_preferences_summary), true, onClick,
    )
}

private fun switchItem(
    context: Context,
    titleRes: Int,
    onRes: Int,
    offRes: Int,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) = SettingItem.Switch(
    context.getString(titleRes),
    context.getString(onRes),
    context.getString(offRes),
    checked,
    onChange,
)

/** Recreate the original dynamic log-level list: raw values + localized labels. */
private fun addLogLevel(
    context: Context,
    items: MutableList<SettingItem>,
    current: Int,
    onChange: (String) -> Unit,
) {
    val values = Logger.getLogLevelsArray().map { it.toString() }.toTypedArray()
    val labels = Logger.getLogLevelLabelsArray(context, Logger.getLogLevelsArray(), true)
        .map { it.toString() }.toTypedArray()
    items += SettingItem.List(
        context.getString(R.string.termux_log_level_title), labels, values, current.toString(), onChange,
    )
}

/** Show the About report, identical to the original background-thread logic. */
private fun showAbout(context: Context) {
    Thread {
        val title = "About"
        val about = StringBuilder()
        about.append(TermuxUtils.getAppInfoMarkdownString(context, TermuxUtils.AppInfoMode.TERMUX_AND_PLUGIN_PACKAGES))
        about.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(context, true))
        about.append("\n\n").append(TermuxUtils.getImportantLinksMarkdownString(context))

        val actionName = UserAction.ABOUT.getName()
        val reportInfo = ReportInfo(actionName, TermuxConstants.TERMUX_APP.TERMUX_SETTINGS_ACTIVITY_NAME, title)
        reportInfo.setReportString(about.toString())
        reportInfo.setReportSaveFileLabelAndPath(
            actionName,
            Environment.getExternalStorageDirectory().toString() + "/" +
                FileUtils.sanitizeFileName(
                    TermuxConstants.TERMUX_APP_NAME + "-" + actionName + ".log", true, true,
                ),
        )
        ReportActivity.startReportActivity(context, reportInfo)
    }.start()
}
