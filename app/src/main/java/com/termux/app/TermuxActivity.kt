package com.termux.app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.text.InputType
import android.util.DisplayMetrics
import android.view.ContextMenu
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.Filter
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.referentialEqualityPolicy
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.termux.R
import com.termux.app.activities.AtomCodeModelsActivity
import com.termux.app.activities.FileEditorActivity
import com.termux.app.activities.HelpActivity
import com.termux.app.activities.SettingsActivity
import com.termux.app.api.file.FileReceiverActivity
import com.termux.app.file.ApkInfoDialog
import com.termux.app.file.ApkInstallUtils
import com.termux.app.file.TextFileDetector
import com.termux.app.models.ExtraKeySymbol
import com.termux.app.models.QuickCommand
import com.termux.app.terminal.TermuxActivityRootView
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalViewClient
import com.termux.app.terminal.io.FullScreenWorkAround
import com.termux.app.terminal.io.TermuxTerminalExtraKeys
import com.termux.app.terminal.ui.DrawerFileItem
import com.termux.app.terminal.ui.DrawerGroupUi
import com.termux.app.terminal.ui.FloatingTerminalBubble
import com.termux.app.terminal.ui.FloatingTerminalPanel
import com.termux.app.terminal.ui.FloatingTerminalState
import com.termux.app.terminal.ui.QuickCommandsPage
import com.termux.app.terminal.ui.QuickCommandsPanel
import com.termux.app.terminal.ui.QuickCommandsPanelState
import com.termux.app.terminal.ui.RestoreRow
import com.termux.app.terminal.ui.SessionTabItem
import com.termux.app.terminal.ui.SessionTabStrip
import com.termux.app.terminal.ui.TermuxDrawer
import com.termux.app.terminal.ui.buildSessionTabs
import com.termux.app.ui.theme.TermuxTheme
import com.termux.shared.activities.ReportActivity
import com.termux.shared.activity.ActivityUtils
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.android.PermissionUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.interact.MessageDialogUtils
import com.termux.shared.logger.Logger
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.interact.TextInputDialogUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.shared.termux.theme.TermuxThemeUtils
import com.termux.shared.theme.NightMode
import com.termux.shared.theme.ThemeUtils
import com.termux.shared.view.KeyboardUtils
import com.termux.shared.view.ViewUtils
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.util.Collections
import kotlin.math.roundToInt

/**
 * The terminal emulator activity.
 *
 * The screen is a native scaffold that hosts the terminal engine plus two Compose content areas:
 * the session tab strip and the left drawer. The engine views ([TerminalView] and
 * [ExtraKeysView]) are `final` classes owned by the terminal modules, so they are hosted as real
 * views and Compose only bridges into them; that keeps keyboard handling, insets, font metrics
 * and the terminal renderer untouched.
 *
 * The floating terminal panel and the quick commands panel are Compose overlays hosted in
 * dedicated `ComposeView` windows attached to the activity content, which keeps their drag,
 * clamping and minimize behaviour in the activity while their content is composable.
 *
 * Every public member here is part of the contract with the Java client classes
 * ([TermuxTerminalViewClient], [TermuxTerminalSessionActivityClient], [TermuxTerminalExtraKeys],
 * [TermuxActivityRootView] and [TermuxService]), so their names and signatures must not change.
 */
class TermuxActivity : AppCompatActivity(), ServiceConnection {

    /** The connection to the [TermuxService], bound in [onCreate]. */
    private var mTermuxService: TermuxService? = null

    /** The terminal view that displays the current session. */
    private var mTerminalView: TerminalView? = null

    /** The [TerminalViewClient] bridging the terminal view and this activity. */
    private var mTermuxTerminalViewClient: TermuxTerminalViewClient? = null

    /** The [com.termux.terminal.TerminalSessionClient] bridging sessions and this activity. */
    private var mTermuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient? = null

    /** Termux app shared preferences manager. */
    private var mPreferences: TermuxAppSharedPreferences? = null

    /** Termux app shared properties loaded from `termux.properties`. */
    private var mProperties: TermuxAppSharedProperties? = null

    /** The root view of the activity, which also handles keyboard insets. */
    private var mTermuxActivityRootView: TermuxActivityRootView? = null

    /** The 1dp space at the bottom of the root view used for keyboard overlap detection. */
    private var mTermuxActivityBottomSpaceView: View? = null

    /** The terminal extra keys view. */
    private var mExtraKeysView: ExtraKeysView? = null

    /** Horizontal scroller around [mExtraKeysView], so a wide toolbar can be panned. */
    private var mTerminalToolbarScroll: HorizontalScrollView? = null

    /** The client for [mExtraKeysView]. */
    private var mTermuxTerminalExtraKeys: TermuxTerminalExtraKeys? = null

    /** The last toast shown, cancelled before a new one is displayed. */
    private var mLastToast: Toast? = null

    /** The receiver for various things like terminal style configuration changes. */
    private val mTermuxActivityBroadcastReceiver = TermuxActivityBroadcastReceiver()

    /** If set, the drawer is not closed in [onStop]; used when opening the file editor. */
    private var mKeepDrawerOpenOnStop = false

    /** The storage accordion cards in the drawer, in top-to-bottom order. */
    private val mDrawerStorageGroups = mutableListOf<DrawerStorageGroup>()

    /** If between [onResume] and [onStop]. */
    private var mIsVisible = false

    /** If [onResume] was called after [onCreate]. */
    private var mIsOnResumeAfterOnCreate = false

    /** If the activity was recreated, e.g. after a style reload or a night mode change. */
    private var mIsActivityRecreated = false

    /** The activity is in an invalid state and must not be run. */
    private var mIsInvalidState = false

    private var mNavBarHeight = 0

    /** Top/bottom system bar insets from the last dispatch, used as a fallback when the content
     * view has no root window insets yet. The overlays are attached to the content view, which
     * can extend behind the status and navigation bars. */
    private var mSystemBarInsetTop = 0
    private var mSystemBarInsetBottom = 0

    private var mTerminalToolbarDefaultHeight = 0f

    // ---------------------------------------------------------------- session tab strip

    /** The tabs currently shown above the terminal; Compose state so the strip recomposes. */
    private val mSessionTabs = mutableStateOf<List<SessionTabItem>>(emptyList())

    // ---------------------------------------------------------------- quick commands panel

    /** The quick commands shown in the panel and the drawer, loaded from preferences. */
    private val mQuickCommands = mutableListOf<QuickCommand>()

    /** Compose state mirror of [mQuickCommands], so the panel recomposes when it changes. */
    private val mQuickCommandsState = mutableStateOf<List<QuickCommand>>(emptyList())

    /** State of the shared quick commands panel, or null when it is closed. */
    private var mQuickCommandsPanelState: QuickCommandsPanelState? = null

    /** The host window of the quick commands panel, or null when closed. */
    private var mQuickCommandsHost: ComposeView? = null

    /** The command being edited on the add/edit page; null when adding a new one. */
    private var mEditingQuickCommand: QuickCommand? = null

    /** Position of the command whose action page is shown, or -1 when none. */
    private var mQuickCommandActionPosition = -1

    /** Indexes of the built-in defaults checked on the restore page. */
    private val mQuickCommandRestoreChecked = mutableStateOf<Set<Int>>(emptySet())

    // ---------------------------------------------------------------- floating terminal

    /** State of the floating terminal panel, or null when it has never been opened. */
    private var mFloatingTerminalState: FloatingTerminalState? = null

    /** The host window of the floating terminal panel, or null when closed. */
    private var mFloatingPanelHost: ComposeView? = null

    /** The host window of the minimized floating terminal bubble, or null when not minimized. */
    private var mFloatingBubbleHost: ComposeView? = null

    /** The terminal view inside the floating panel, attached to its own session. */
    private var mFloatingTerminalView: TerminalView? = null

    /** Last command run in the floating terminal panel, used by its re-run button. */
    private var mFloatingPanelCommand: String? = null

    /** A command requested before the panel's TerminalView existed; started once it is created. */
    private var mPendingFloatingCommand: String? = null

    /** Visual top edge the floating terminal panel returns to when restored; NaN when unset. */
    private var mFloatingTerminalAnchorTop = Float.NaN

    /** Remembered dock side and top of the minimized bubble; NaN top until it has been docked. */
    private var mFloatingTerminalBubbleDockRight = false
    private var mFloatingTerminalBubbleTop = Float.NaN

    companion object {
        /** Preference keys for the last browsed directory of each storage card. */
        const val PREF_KEY_DRAWER_FILE_LIST_DIR_FILES = "drawer_file_list_dir_files"
        const val PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_FILES = "drawer_file_list_dir_external_files"
        const val PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_SHARED = "drawer_file_list_dir_external_shared"

        /** Key for the persisted quick commands JSON array in the activity preferences. */
        const val KEY_QUICK_COMMANDS = "drawer_quick_commands"

        /** Key for the persisted JSON array of symbols added to the terminal toolbar. */
        const val KEY_EXTRA_KEY_SYMBOLS = "extra_key_symbols"

        const val ARG_ACTIVITY_RECREATED = "activity_recreated"

        const val LOG_TAG = "TermuxActivity"

        const val CONTEXT_MENU_SELECT_URL_ID = 0
        const val CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1
        const val CONTEXT_MENU_SHARE_SELECTED_TEXT = 10
        const val CONTEXT_MENU_AUTOFILL_USERNAME = 11
        const val CONTEXT_MENU_AUTOFILL_PASSWORD = 2
        const val CONTEXT_MENU_RESET_TERMINAL_ID = 3
        const val CONTEXT_MENU_KILL_PROCESS_ID = 4
        const val CONTEXT_MENU_STYLING_ID = 5
        const val CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6
        const val CONTEXT_MENU_HELP_ID = 7
        const val CONTEXT_MENU_SETTINGS_ID = 8
        const val CONTEXT_MENU_REPORT_ID = 9

        /** Built-in default quick commands used to initialize the persisted list on first run. */
        val DEFAULT_COMMANDS = arrayOf(
            arrayOf("更新源", "apt update && apt upgrade -y"),
            arrayOf("安装 zsh", "pkg install -y zsh git vim"),
            arrayOf("安装 ohmyzsh", "sh -c \"\$(curl -fsSL https://install.ohmyz.sh/)\""),
            arrayOf(
                "安装 zsh-autosuggestions",
                "git clone https://github.com/zsh-users/zsh-autosuggestions \${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-autosuggestions && (grep -q \"zsh-autosuggestions\" ~/.zshrc || sed -i 's/^plugins=(\\([^)]*\\))/plugins=(\\1 zsh-autosuggestions)/' ~/.zshrc)"
            ),
            arrayOf(
                "安装 atomcode",
                "curl -fsSL https://raw.atomgit.com/atomgit_atomcode/atomcode/raw/main/scripts/install.sh | sh"
            ),
            arrayOf("请求存储权限", "termux-setup-storage"),
        )

        val COMMAND_ICONS = intArrayOf(
            R.drawable.ic_drawer_refresh,
            R.drawable.ic_drawer_terminal,
            R.drawable.ic_drawer_ohmyzsh,
            R.drawable.ic_drawer_extension,
            R.drawable.ic_drawer_code,
            R.drawable.ic_drawer_bolt,
            R.drawable.ic_drawer_folder,
        )

        /** Make sure that the terminal styling is always applied. */
        @JvmStatic
        fun updateTermuxActivityStyling(context: Context, recreateActivity: Boolean) {
            val stylingIntent = Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE)
            stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity)
            context.sendBroadcast(stylingIntent)
        }

        @JvmStatic
        fun startTermuxActivity(context: Context) {
            ActivityUtils.startActivity(context, newInstance(context))
        }

        @JvmStatic
        fun newInstance(context: Context): Intent =
            Intent(context, TermuxActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.logDebug(LOG_TAG, "onCreate")
        mIsOnResumeAfterOnCreate = true

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false)

        // Delete ReportInfo serialized object files from cache older than 14 days.
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false)

        // Load Termux app SharedProperties from disk.
        mProperties = TermuxAppSharedProperties.getProperties()
        reloadProperties()

        setActivityTheme()

        super.onCreate(savedInstanceState)

        // Edge-to-edge (same as the Compose demo): draw content behind the transparent status
        // bar instead of letting the system push everything down. The terminal gets its top
        // inset back explicitly below; the drawer gradient is then free to reach y=0.
        // The no-arg overload gives the navigation bar a translucent contrast scrim
        // (DefaultLightScrim 0xE6FFFFFF / DefaultDarkScrim 0x801B1B1B in Activity 1.8.2) while the
        // status bar gets a fully transparent one (auto(0,0)). That scrim is what leaves a bar
        // at the bottom. Pass a transparent scrim for the navigation bar too so both system bars
        // are symmetric and fully edge-to-edge; icon colours are handled by
        // updateStatusBarAppearance() below.
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            )
        )
        // enableEdgeToEdge enables navigation-bar contrast enforcement on API 29+ because
        // SystemBarStyle.auto() reports nightMode == 0. That makes the system draw its own
        // translucent scrim over the transparent navigation bar, which shows up as a solid dark
        // band over the drawer bottom on dark content. Disable it so the bar is truly transparent.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        setContentView(buildContentView())

        // Load termux shared preferences. This also fails if TermuxConstants.TERMUX_PACKAGE_NAME
        // does not equal applicationId.
        mPreferences = TermuxAppSharedPreferences.build(this, true)
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so don't continue running.
            mIsInvalidState = true
            return
        }

        setMargins()

        // The status bar is transparent and shows the decor background through it, so its icon
        // colours must follow that background's luminance (dark icons over a light terminal).
        updateStatusBarAppearance()

        val content = findViewById<View>(android.R.id.content)
        content.setOnApplyWindowInsetsListener { v, insets ->
            val windowInsets = WindowInsetsCompat.toWindowInsetsCompat(insets)
            mNavBarHeight = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            mSystemBarInsetTop = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            mSystemBarInsetBottom = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            // Insets are handled edge-to-edge: the terminal content and the drawer each apply
            // their own system-bar padding via ViewCompat/Compose WindowInsets, so no view-level
            // translation is needed to reach behind the transparent status bar.
            // Insets may change while an overlay is already on screen; clamp it again once the
            // layout has settled.
            if (mQuickCommandsHost != null || mFloatingPanelHost != null) {
                v.post {
                    mQuickCommandsHost?.let { clampOverlayTranslation(it) }
                    mFloatingPanelHost?.let { clampOverlayTranslation(it) }
                }
            }
            insets
        }

        // Edge-to-edge requires the status bar to stay visible (transparent) so the drawer can
        // draw behind it. FLAG_FULLSCREEN would hide/black out the bar entirely, defeating this,
        // so it is intentionally not added here even when the fullscreen preference is on.
        // The terminal content applies its own top inset, so nothing is covered.

        setTermuxTerminalViewAndClients()
        setTerminalToolbarView()
        setSessionTabStrip()
        setDrawer()
        registerForContextMenu(mTerminalView!!)

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this)

        try {
            // Start the TermuxService and make it run regardless of who is bound to it.
            val serviceIntent = Intent(this, TermuxService::class.java)
            startService(serviceIntent)

            // Attempt to bind to the service; this calls onServiceConnected() on success.
            if (!bindService(serviceIntent, this, 0))
                throw RuntimeException("bindService() failed")
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e)
            Logger.showToast(
                this,
                getString(
                    if (e.message != null && e.message!!.contains("app is in background"))
                        R.string.error_termux_service_start_failed_bg
                    else
                        R.string.error_termux_service_start_failed_general
                ),
                true
            )
            mIsInvalidState = true
            return
        }

        // Notify apps that the Termux app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this)
    }

    override fun onStart() {
        super.onStart()

        Logger.logDebug(LOG_TAG, "onStart")

        if (mIsInvalidState) return

        mIsVisible = true

        mTermuxTerminalSessionActivityClient?.onStart()
        mTermuxTerminalViewClient?.onStart()

        if (mPreferences?.isTerminalMarginAdjustmentEnabled == true)
            addTermuxActivityRootViewGlobalLayoutListener()

        registerTermuxActivityBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()

        Logger.logVerbose(LOG_TAG, "onResume")

        if (mIsInvalidState) return

        mTermuxTerminalSessionActivityClient?.onResume()
        mTermuxTerminalViewClient?.onResume()

        // Show a notification with crash details if the app or a plugin crashed on the last run.
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG)

        mIsOnResumeAfterOnCreate = false
    }

    override fun onStop() {
        super.onStop()

        Logger.logDebug(LOG_TAG, "onStop")

        if (mIsInvalidState) return

        mIsVisible = false

        mTermuxTerminalSessionActivityClient?.onStop()
        mTermuxTerminalViewClient?.onStop()

        removeTermuxActivityRootViewGlobalLayoutListener()

        unregisterTermuxActivityBroadcastReceiver()
        if (!mKeepDrawerOpenOnStop) {
            getDrawer()?.closeDrawers()
        }
        mKeepDrawerOpenOnStop = false
    }

    override fun onDestroy() {
        super.onDestroy()

        Logger.logDebug(LOG_TAG, "onDestroy")

        if (mIsInvalidState) return

        // Remove the floating overlays if they are still attached.
        hideFloatingTerminalPanel()
        hideQuickCommandsOverlay()

        mTermuxService?.let { service ->
            // Do not leave service and session clients with references to the activity.
            service.unsetTermuxTerminalSessionClient()
            mTermuxService = null
        }

        try {
            unbindService(this)
        } catch (e: Exception) {
            // ignore.
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState")

        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true)
    }

    override fun onServiceConnected(componentName: ComponentName?, service: IBinder?) {
        Logger.logDebug(LOG_TAG, "onServiceConnected")

        mTermuxService = (service as TermuxService.LocalBinder).service

        val intent = intent
        setIntent(null)

        val termuxService = mTermuxService ?: return

        if (termuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(this) {
                    if (mTermuxService == null) return@setupBootstrapIfNeeded // Activity may be destroyed.
                    try {
                        var launchFailsafe = false
                        if (intent != null && intent.extras != null) {
                            launchFailsafe = intent.extras!!
                                .getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false)
                        }
                        mTermuxTerminalSessionActivityClient?.addNewSession(launchFailsafe, null)
                    } catch (e: WindowManager.BadTokenException) {
                        // Activity finished - ignore.
                    }
                }
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing()
            }
        } else {
            // If Termux was started from the launcher "New session" shortcut and the activity is
            // recreated, the original intent is re-delivered, which would add a new session each
            // time, so only honour it when the activity was not recreated.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN == intent.action) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                val isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false)
                mTermuxTerminalSessionActivityClient?.addNewSession(isFailSafe, null)
            } else {
                mTermuxTerminalSessionActivityClient?.let { client ->
                    client.setCurrentSession(client.getCurrentStoredSessionOrLast())
                }
            }
        }

        // Update the TerminalSession and TerminalEmulator clients.
        termuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient)

        // A session may already have been created synchronously above, before the client was set,
        // so refresh the tabs now to make sure they are up to date.
        termuxSessionListNotifyUpdated()
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected")

        // Respect being stopped from the TermuxService notification action.
        finishActivityIfNotFinishing()
    }

    // ---------------------------------------------------------------- properties and theme

    private fun reloadProperties() {
        mProperties?.loadTermuxPropertiesFromDisk()
        mTermuxTerminalViewClient?.onReloadProperties()
    }

    private fun setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE.
        TermuxThemeUtils.setAppNightMode(mProperties?.nightMode)

        // Set the activity night mode. If NightMode.SYSTEM is set, android recreates the activity
        // automatically when the uiMode configuration changes so the day/night theme applies.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true)
    }

    // ---------------------------------------------------------------- content view

    /**
     * Build the terminal screen scaffold in code: the root view that handles keyboard insets, the
     * drawer holding the terminal content and the Compose drawer, and the 1dp bottom space view
     * used for keyboard overlap detection.
     *
     * The scaffold used to live in `activity_termux.xml`; building it here keeps every view id the
     * client classes look up while leaving no layout resource behind. The engine views
     * ([TerminalView] and [ExtraKeysView]) are created with their original attributes so the
     * renderer, scrollbars and extra keys keep behaving exactly as before.
     */
    private fun buildContentView(): View {
        val root = TermuxActivityRootView(this).apply {
            id = R.id.activity_termux_root_view
            orientation = LinearLayout.VERTICAL
            // Edge-to-edge: do not let the root consume the top inset, otherwise the whole tree
            // (including the drawer) is pushed below the transparent status bar. Only the bottom
            // inset is applied (keyboard workaround relies on it); the terminal content and the
            // drawer handle the top inset themselves via the official WindowInsets APIs.
            fitsSystemWindows = false
        }
        mTermuxActivityRootView = root
        root.setActivity(this)
        root.setOnApplyWindowInsetsListener(TermuxActivityRootView.WindowInsetsListener())

        val relativeLayout = RelativeLayout(this).apply {
            id = R.id.activity_termux_root_relative_layout
        }
        root.addView(
            relativeLayout,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        val drawerLayout = DrawerLayout(this).apply {
            id = R.id.drawer_layout
            // Let the lifted drawer child render into the status bar area above this layout.
            clipChildren = false
        }
        relativeLayout.addView(
            drawerLayout,
            RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ).apply { addRule(RelativeLayout.ALIGN_PARENT_TOP) }
        )

        val contentLayout = LinearLayout(this).apply {
            id = R.id.terminal_content_layout
            orientation = LinearLayout.VERTICAL
        }
        // Full edge-to-edge: the root consumes no insets, so the terminal content applies its own
        // top AND bottom padding to stay clear of the status/navigation bar icons. The drawer, in
        // contrast, lets its background draw behind both bars. Use the official WindowInsets API,
        // and pass the insets through so sibling views (the drawer) still receive them.
        ViewCompat.setOnApplyWindowInsetsListener(contentLayout) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
        drawerLayout.addView(
            contentLayout,
            DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        // Session tab strip, drawn by Compose.
        val tabStrip = ComposeView(this).apply {
            id = R.id.terminal_sessions_tab_strip
            setBackgroundColor(Color.BLACK)
        }
        contentLayout.addView(
            tabStrip,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        // Terminal view: a theme overlay supplies the custom scrollbar thumb the layout used to set.
        val terminalView = TerminalView(ContextThemeWrapper(this, R.style.TermuxTerminalView), null).apply {
            id = R.id.terminal_view
            isFocusableInTouchMode = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                defaultFocusHighlightEnabled = false
            }
        }
        contentLayout.addView(
            terminalView,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        // Extra keys toolbar, hidden until the preference enables it.
        val toolbarContainer = LinearLayout(this).apply {
            id = R.id.terminal_toolbar_container
            orientation = LinearLayout.VERTICAL
        }
        contentLayout.addView(
            toolbarContainer,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val toolbarScroll = HorizontalScrollView(this).apply {
            id = R.id.terminal_toolbar_scroll
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            visibility = View.GONE
        }
        toolbarContainer.addView(
            toolbarScroll,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val extraKeysView = ExtraKeysView(this, null).apply {
            id = R.id.terminal_toolbar_extra_keys
            setBackgroundColor(Color.BLACK)
        }
        toolbarScroll.addView(
            extraKeysView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (37.5f * resources.displayMetrics.density).roundToInt()
            )
        )

        // Drawer content, drawn by Compose.
        val drawerView = ComposeView(this).apply { id = R.id.left_drawer }
        val drawerLp = DrawerLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.START,
        ).apply {
            // DrawerLayout reserves a hardcoded 64dp gap at the screen edge (MIN_DRAWER_MARGIN)
            // when measuring a drawer. Cancel it with an equal negative margin so the drawer
            // can cover the full screen width instead of leaving a strip of terminal visible.
            leftMargin = -(64f * resources.displayMetrics.density).roundToInt()
        }
        drawerLayout.addView(drawerView, drawerLp)

        // 1dp transparent space used to detect whether the keyboard covers the extra keys.
        val bottomSpaceView = View(this).apply {
            id = R.id.activity_termux_bottom_space_view
            setBackgroundColor(Color.TRANSPARENT)
        }
        mTermuxActivityBottomSpaceView = bottomSpaceView
        root.addView(
            bottomSpaceView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (1f * resources.displayMetrics.density).roundToInt()
            )
        )

        return root
    }

    private fun setMargins() {
        val relativeLayout = findViewById<RelativeLayout>(R.id.activity_termux_root_relative_layout)
            ?: return
        val marginVertical = mProperties?.terminalMarginVertical ?: 0
        // 水平 margin 固定为 0：否则 RelativeLayout（DrawerLayout 父容器）会被整体右移，
        // 导致侧栏左缘无法紧贴屏幕边缘，露出黑色窗口背景形成黑线。
        ViewUtils.setLayoutMarginsInDp(relativeLayout, 0, marginVertical, 0, marginVertical)
    }

    /** With edge-to-edge the status bar is transparent and shows whatever is drawn behind it:
     * the terminal when the drawer is closed, or the drawer's top colour when it is open. So the
     * bars themselves are never tinted; only the icon colours follow the blended luminance so
     * they stay readable over both a dark terminal and a light drawer. */
    @JvmOverloads
    fun updateStatusBarAppearance(drawerOffset: Float = 0f) {
        val drawerOffsetRatio = drawerOffset.coerceIn(0f, 1f)
        val terminalColor = (window.decorView.background as? ColorDrawable)?.color ?: Color.BLACK
        val drawerColor = ContextCompat.getColor(this, R.color.drawer_background_start)
        val color = ColorUtils.blendARGB(
            terminalColor, drawerColor, drawerOffsetRatio
        )
        val lightIcons = ColorUtils.calculateLuminance(color) > 0.5f
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightIcons
        controller.isAppearanceLightNavigationBars = lightIcons
    }

    fun addTermuxActivityRootViewGlobalLayoutListener() {
        val rootView = mTermuxActivityRootView ?: return
        rootView.viewTreeObserver.addOnGlobalLayoutListener(rootView)
    }

    fun removeTermuxActivityRootViewGlobalLayoutListener() {
        val rootView = mTermuxActivityRootView ?: return
        rootView.viewTreeObserver.removeOnGlobalLayoutListener(rootView)
    }

    // ---------------------------------------------------------------- terminal view and toolbar

    private fun setTermuxTerminalViewAndClients() {
        // Set the termux terminal view and session clients.
        val sessionClient = TermuxTerminalSessionActivityClient(this)
        mTermuxTerminalSessionActivityClient = sessionClient
        val viewClient = TermuxTerminalViewClient(this, sessionClient)
        mTermuxTerminalViewClient = viewClient

        // Set the termux terminal view.
        val terminalView = findViewById<TerminalView>(R.id.terminal_view)
        mTerminalView = terminalView
        terminalView.setTerminalViewClient(viewClient)

        viewClient.onCreate()
        sessionClient.onCreate()
    }

    private fun setTerminalToolbarView() {
        val terminalView = mTerminalView ?: return

        val extraKeys = TermuxTerminalExtraKeys(
            this, terminalView, mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient
        )
        mTermuxTerminalExtraKeys = extraKeys

        val extraKeysView = findViewById<ExtraKeysView>(R.id.terminal_toolbar_extra_keys)
        mExtraKeysView = extraKeysView
        mTerminalToolbarScroll = findViewById(R.id.terminal_toolbar_scroll)

        if (mPreferences?.shouldShowTerminalToolbar() == true)
            mTerminalToolbarScroll?.visibility = View.VISIBLE

        mTerminalToolbarDefaultHeight = extraKeysView.layoutParams.height.toFloat()

        extraKeysView.setExtraKeysViewClient(extraKeys)
        extraKeysView.setButtonTextAllCaps(mProperties?.shouldExtraKeysTextBeAllCaps() == true)
        extraKeysView.reload(extraKeys.extraKeysInfo, mTerminalToolbarDefaultHeight)

        // Apply the extra keys fix if enabled in prefs.
        if (mProperties?.isUsingFullScreen == true && mProperties?.isUsingFullScreenWorkAround == true) {
            FullScreenWorkAround.apply(this)
        }

        setTerminalToolbarHeight()
    }

    private fun setTerminalToolbarHeight() {
        val extraKeysView = mExtraKeysView ?: return
        val extraKeys = mTermuxTerminalExtraKeys ?: return

        val layoutParams = extraKeysView.layoutParams
        layoutParams.height = (mTerminalToolbarDefaultHeight *
            extraKeys.rowCount *
            (mProperties?.terminalToolbarHeightScaleFactor ?: 1f)).roundToInt()
        extraKeysView.layoutParams = layoutParams
        updateTerminalToolbarWidth()
    }

    /**
     * Keep every column at a readable minimum width; when the resulting toolbar is wider than the
     * screen it scrolls horizontally inside [mTerminalToolbarScroll] instead of squeezing all
     * buttons into the available width.
     */
    private fun updateTerminalToolbarWidth() {
        val extraKeysView = mExtraKeysView ?: return
        val extraKeys = mTermuxTerminalExtraKeys ?: return

        val columns = extraKeys.columnCount
        val minColumnWidth = (48 * resources.displayMetrics.density).toInt()
        val viewportWidth = resources.displayMetrics.widthPixels

        // A HorizontalScrollView always measures its child with an unspecified width, so setting
        // the view width alone never makes it scroll. Giving every column a fixed width instead
        // makes the grid measure wider than the screen, which enables the scrolling.
        val scrolls = columns.toLong() * minColumnWidth > viewportWidth
        val fixedColumnWidth = if (scrolls) minColumnWidth else 0

        if (extraKeysView.fixedColumnWidth != fixedColumnWidth) {
            extraKeysView.fixedColumnWidth = fixedColumnWidth
            // The width is applied while the buttons are created, so rebuild them.
            extraKeysView.reload(extraKeys.extraKeysInfo, mTerminalToolbarDefaultHeight)
        }
    }

    fun toggleTerminalToolbar() {
        val toolbarScroll = mTerminalToolbarScroll ?: return
        val preferences = mPreferences ?: return

        val showNow = preferences.toogleShowTerminalToolbar()
        Logger.showToast(
            this,
            if (showNow) getString(R.string.msg_enabling_terminal_toolbar)
            else getString(R.string.msg_disabling_terminal_toolbar),
            true
        )
        toolbarScroll.visibility = if (showNow) View.VISIBLE else View.GONE
    }

    // ---------------------------------------------------------------- session tab strip

    /** Build the Compose tab strip above the terminal and push the initial session list. */
    private fun setSessionTabStrip() {
        val tabStrip = findViewById<ComposeView>(R.id.terminal_sessions_tab_strip) ?: return

        tabStrip.setContent {
            TermuxTheme {
                SessionTabStrip(
                    tabs = mSessionTabs.value,
                    onSelect = { tab -> selectSessionByHandle(tab.handle) },
                    onRename = { tab -> findTermuxSession(tab.handle)?.let { renameSession(it) } },
                    onClose = { tab -> findTermuxSession(tab.handle)?.let { confirmCloseSession(it) } },
                    onNewSession = { mTermuxTerminalSessionActivityClient?.addNewSession(false, null) },
                    onNewSessionLongClick = { showCreateNamedSessionDialog() },
                )
            }
        }
    }

    /** The sessions shown in the main interface's tab list; overlay-only sessions are excluded. */
    private fun getTermuxSessionsForTabList(): List<TermuxSession> {
        val service = mTermuxService ?: return emptyList()
        return service.termuxSessions.filterNot { it.executionCommand?.isFloatingPanelSession == true }
    }

    /** Find the session behind a tab handle, skipping the sessions shown in the floating panel. */
    private fun findTermuxSession(handle: String): TermuxSession? =
        getTermuxSessionsForTabList().firstOrNull { it.terminalSession?.mHandle == handle }

    private fun selectSessionByHandle(handle: String) {
        val session = findTermuxSession(handle)?.terminalSession ?: return
        mTermuxTerminalSessionActivityClient?.setCurrentSession(session)
    }

    /** Rebuild the tab list from the service and scroll to the current session. */
    fun termuxSessionListNotifyUpdated() {
        if (mTermuxService == null) return
        mSessionTabs.value = buildSessionTabs(getTermuxSessionsForTabList(), getCurrentSession()?.mHandle)
    }

    private fun showCreateNamedSessionDialog() {
        TextInputDialogUtils.textInput(
            this,
            R.string.title_create_named_session,
            null,
            R.string.action_create_named_session_confirm,
            { text -> mTermuxTerminalSessionActivityClient?.addNewSession(false, text) },
            R.string.action_new_session_failsafe,
            { text -> mTermuxTerminalSessionActivityClient?.addNewSession(true, text) },
            -1,
            null,
            null
        )
    }

    private fun renameSession(termuxSession: TermuxSession) {
        val session = termuxSession.terminalSession ?: return
        mTermuxTerminalSessionActivityClient?.renameSession(session)
    }

    private fun confirmCloseSession(termuxSession: TermuxSession) {
        val terminalSession = termuxSession.terminalSession ?: return
        val sessionTitle = terminalSession.mSessionName?.takeIf { it.isNotEmpty() } ?: "session"

        MessageDialogUtils.showMessage(
            this,
            getString(R.string.title_confirm_close_session),
            getString(R.string.msg_confirm_close_session, sessionTitle),
            getString(R.string.action_close_session),
            DialogInterface.OnClickListener { _, _ -> closeSession(termuxSession) },
            getString(android.R.string.cancel),
            null,
            null
        )
    }

    private fun closeSession(termuxSession: TermuxSession) {
        val service = mTermuxService ?: return
        val terminalSession = termuxSession.terminalSession ?: return

        val index = service.getIndexOfSession(terminalSession)
        if (index < 0) return

        // Kill the session if it is still running.
        if (terminalSession.isRunning) terminalSession.finishIfRunning()
        termuxSession.killIfExecuting(this, true)

        // Remove the session from the list and switch to the session that took its place,
        // instead of waiting for the onSessionFinished callback.
        service.removeTermuxSession(terminalSession)

        val size = service.termuxSessionsSize
        if (size == 0) {
            finishActivityIfNotFinishing()
        } else {
            val neighbourIndex = if (index >= size) size - 1 else index
            service.getTermuxSession(neighbourIndex)?.let { neighbour ->
                mTermuxTerminalSessionActivityClient?.setCurrentSession(neighbour.terminalSession)
            }
        }

        termuxSessionListNotifyUpdated()
    }

    // ---------------------------------------------------------------- left drawer

    /** Build the drawer: the three storage cards and the Compose content. */
    private fun setDrawer() {
        val drawer = getDrawer() ?: return

        // Keep the drawer in its default (unlocked) state so it can be opened from the edge and
        // closed with a swipe, just like a standard DrawerLayout.
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Blend the status bar into the drawer top colour as it slides in.
                updateStatusBarAppearance(slideOffset)
            }

            override fun onDrawerOpened(drawerView: View) {
                // Hide the soft keyboard so it does not cover the drawer content.
                mTerminalView?.let { KeyboardUtils.hideSoftKeyboard(this@TermuxActivity, it) }
                updateStatusBarAppearance(1f)
            }

            override fun onDrawerClosed(drawerView: View) {
                updateStatusBarAppearance(0f)
            }
        })

        mDrawerStorageGroups.clear()
        mDrawerStorageGroups.add(
            DrawerStorageGroup(
                root = TermuxConstants.TERMUX_FILES_DIR,
                unavailableMessageId = R.string.msg_external_files_dir_unavailable,
                dirPrefKey = PREF_KEY_DRAWER_FILE_LIST_DIR_FILES,
                title = "内部存储",
            )
        )
        mDrawerStorageGroups.add(
            DrawerStorageGroup(
                root = getExternalFilesDir(null),
                unavailableMessageId = R.string.msg_external_files_dir_unavailable,
                dirPrefKey = PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_FILES,
                title = "外部存储",
            )
        )
        mDrawerStorageGroups.add(
            DrawerStorageGroup(
                root = Environment.getExternalStorageDirectory(),
                unavailableMessageId = R.string.msg_external_shared_dir_unavailable,
                dirPrefKey = PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_SHARED,
                title = "共享存储",
            )
        )

        val drawerView = findViewById<ComposeView>(R.id.left_drawer) ?: return
        drawerView.setContent {
            TermuxTheme {
                TermuxDrawer(
                    groups = mDrawerStorageGroups,
                    onOpenAtomCodeModels = {
                        ActivityUtils.startActivity(this, Intent(this, AtomCodeModelsActivity::class.java))
                    },
                    onOpenSettings = {
                        ActivityUtils.startActivity(this, Intent(this, SettingsActivity::class.java))
                    },
                )
            }
        }
    }

    /**
     * One storage accordion card in the drawer: a header row (tap to expand/collapse, a "go up"
     * button to navigate one level up) and a directory list. Only one directory level is shown at
     * a time; tapping a directory enters it.
     *
     * Every value the Compose drawer reads is Compose state, so expanding a card, entering a
     * directory or reloading its listing recomposes the drawer.
     */
    private inner class DrawerStorageGroup(
        private val root: File?,
        private val unavailableMessageId: Int,
        private val dirPrefKey: String,
        override val title: String,
    ) : DrawerGroupUi {

        /** The directory currently shown; equals [root] at the top level. */
        private var currentDir by mutableStateOf<File?>(null)

        /** Whether the directory list is currently expanded. */
        override var expanded by mutableStateOf(false)
            private set

        override var isLoading by mutableStateOf(false)
            private set

        override var items by mutableStateOf<List<DrawerFileItem>>(
            emptyList(), referentialEqualityPolicy()
        )
            private set

        private var loadJob: Job? = null
        private val loadMutex = Mutex()
        private var loadGeneration = 0
        private var loadedListing: Pair<File, List<DrawerFileItem>>? = null

        override val canGoUp: Boolean
            get() = expanded && currentDir != null && !isAtRoot()

        override val pathText: String
            get() {
                val dir = currentDir ?: root ?: return ""
                val base = root
                val path = dir.absolutePath
                return if (base != null && dir != base && path.startsWith(base.absolutePath))
                    path.substring(base.absolutePath.length)
                else
                    path
            }

        /** Whether the list currently shows the storage root directory. */
        private fun isAtRoot(): Boolean = root != null && currentDir == root

        override fun toggleExpanded() {
            if (expanded) {
                expanded = false
                ++loadGeneration
                loadJob?.cancel()
                loadJob = null
                isLoading = false
                return
            }
            if (root == null) {
                Toast.makeText(this@TermuxActivity, unavailableMessageId, Toast.LENGTH_SHORT).show()
                return
            }
            expanded = true
            if (!isLoading) refresh()
        }

        override fun goUp() {
            if (isAtRoot()) return
            val parent = currentDir?.parentFile ?: return
            currentDir = parent
            refresh()
        }

        override fun enterDirectory(dir: File) {
            expanded = true
            if (currentDir == dir && isLoading) return
            currentDir = dir
            refresh()
        }

        override fun openFile(file: File) {
            when {
                file.isDirectory -> enterDirectory(file)

                ApkInstallUtils.isApkFile(file) -> {
                    // APK: show its metadata (and install it) without leaving the drawer.
                    ApkInfoDialog.show(this@TermuxActivity, file)
                }

                TextFileDetector.isTextFile(file) -> {
                    // Text file: open it in the sora-editor based text editor. Keep the drawer
                    // open so that it is still open when coming back from the editor.
                    mKeepDrawerOpenOnStop = true
                    val intent = Intent(this@TermuxActivity, FileEditorActivity::class.java)
                    intent.putExtra(FileEditorActivity.EXTRA_FILE_PATH, file.absolutePath)
                    startActivity(intent)
                }

                else -> Toast.makeText(this@TermuxActivity, file.absolutePath, Toast.LENGTH_SHORT).show()
            }
        }

        override fun copyPath() {
            val dir = currentDir ?: root ?: return
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager ?: return
            clipboard.setPrimaryClip(ClipData.newPlainText(null, dir.absolutePath))
            Toast.makeText(this@TermuxActivity, dir.absolutePath, Toast.LENGTH_SHORT).show()
        }

        /** The last browsed directory, or null if nothing was saved or it no longer exists. */
        private fun getLastDir(): File? {
            if (root == null) return null
            val savedPath = getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, MODE_PRIVATE
            ).getString(dirPrefKey, null) ?: return null
            val savedDir = File(savedPath)
            return if (savedDir.isDirectory) savedDir else null
        }

        /** Persist the directory currently shown so it is restored on recreation. */
        private fun saveDir() {
            val dir = currentDir ?: return
            getSharedPreferences(
                TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, MODE_PRIVATE
            ).edit().putString(dirPrefKey, dir.absolutePath).apply()
        }

        /** Reload in the background, publishing only the latest complete directory listing. */
        private fun refresh() {
            val storageRoot = root ?: return
            val requestedDir = currentDir
            val generation = ++loadGeneration
            loadJob?.cancel()
            isLoading = true
            items = loadedListing?.takeIf { it.first == requestedDir }?.second ?: emptyList()

            loadJob = lifecycleScope.launch {
                try {
                    val listing = withContext(Dispatchers.IO) {
                        loadMutex.withLock {
                            try {
                                ensureActive()
                                if (!storageRoot.isDirectory) return@withLock null
                                val dir = requestedDir ?: getLastDir() ?: storageRoot
                                if (!dir.isDirectory) return@withLock null
                                val files = dir.listFiles() ?: return@withLock null
                                val entries = ArrayList<DrawerFileItem>(files.size)
                                for (file in files) {
                                    ensureActive()
                                    val isDirectory = file.isDirectory
                                    entries.add(
                                        DrawerFileItem(
                                            file = file,
                                            isDirectory = isDirectory,
                                            isApk = !isDirectory &&
                                                file.name.endsWith(".apk", ignoreCase = true) &&
                                                ApkInstallUtils.isApkFile(file),
                                        )
                                    )
                                }
                                entries.sortWith(Comparator { first, second ->
                                    ensureActive()
                                    when {
                                        first.isDirectory != second.isDirectory ->
                                            if (first.isDirectory) -1 else 1
                                        else -> String.CASE_INSENSITIVE_ORDER.compare(first.name, second.name)
                                    }
                                })
                                ensureActive()
                                dir to entries
                            } catch (_: SecurityException) {
                                null
                            }
                        }
                    }
                    if (generation != loadGeneration) return@launch
                    if (listing == null) {
                        currentDir = loadedListing?.first
                        items = loadedListing?.second ?: emptyList()
                        if (loadedListing == null) expanded = false
                        Toast.makeText(this@TermuxActivity, unavailableMessageId, Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    currentDir = listing.first
                    loadedListing = listing
                    items = listing.second
                    saveDir()
                } finally {
                    if (generation == loadGeneration) isLoading = false
                }
            }
        }
    }

    // ---------------------------------------------------------------- quick commands

    /**
     * Load the quick commands from preferences, falling back to the built-in defaults (which are
     * then persisted) when nothing has been saved yet.
     */
    private fun loadQuickCommands() {
        mQuickCommands.clear()
        val json = activityPreferences().getString(KEY_QUICK_COMMANDS, null)
        var loaded = false
        if (json != null) {
            try {
                val array = JSONArray(json)
                for (i in 0 until array.length()) {
                    val quickCommand = QuickCommand.fromJson(array.getJSONObject(i))
                    sanitizeQuickCommandIcon(quickCommand)
                    mQuickCommands.add(quickCommand)
                }
                loaded = mQuickCommands.isNotEmpty()
            } catch (e: JSONException) {
                Logger.logError(LOG_TAG, "Failed to parse saved quick commands: " + e.message)
            }
        }
        if (!loaded) {
            for (i in DEFAULT_COMMANDS.indices) {
                mQuickCommands.add(
                    QuickCommand(
                        DEFAULT_COMMANDS[i][0],
                        DEFAULT_COMMANDS[i][1],
                        COMMAND_ICONS[i % COMMAND_ICONS.size],
                    )
                )
            }
            saveQuickCommands()
        }
        syncQuickCommandsState()
    }

    /** Persist the current quick commands list as JSON. */
    private fun saveQuickCommands() {
        val array = JSONArray()
        for (quickCommand in mQuickCommands) {
            // Also persist the icon by name, since resource ids are not stable across builds.
            quickCommand.iconName = quickCommandIconName(quickCommand.iconRes)
            array.put(quickCommand.toJson())
        }
        activityPreferences().edit().putString(KEY_QUICK_COMMANDS, array.toString()).apply()
        syncQuickCommandsState()
    }

    /** Refresh the Compose mirror of the command list. */
    private fun syncQuickCommandsState() {
        mQuickCommandsState.value = mQuickCommands.toList()
    }

    private fun activityPreferences(): SharedPreferences =
        getSharedPreferences("termux_activity", Context.MODE_PRIVATE)

    /** Assign the next icon from the built-in icon pool for a newly created command. */
    private fun nextQuickCommandIcon(): Int = COMMAND_ICONS[mQuickCommands.size % COMMAND_ICONS.size]

    /** Resolve a persisted icon name back to its current drawable id, or 0 if the drawable does not
     * exist anymore, which happens when the resources of the app changed between builds. */
    private fun resolveQuickCommandIcon(iconName: String?): Int {
        if (iconName.isNullOrEmpty()) return 0
        return resources.getIdentifier(iconName, "drawable", packageName)
    }

    /** The drawable entry name to persist for an icon, so that it can be resolved again later since
     * resource ids are not stable across builds. */
    private fun quickCommandIconName(iconRes: Int): String? =
        runCatching { resources.getResourceEntryName(iconRes) }.getOrNull()

    /** Replace an icon that can no longer be drawn, e.g. one whose resource id is from an older build
     * or now points to another resource, so that painting it cannot crash the Compose panel. Icons
     * are cosmetic, so an unknown one falls back to the first icon of the built-in pool. */
    private fun sanitizeQuickCommandIcon(quickCommand: QuickCommand) {
        val resolvedIcon = resolveQuickCommandIcon(quickCommand.iconName)
        quickCommand.iconRes = when {
            resolvedIcon in COMMAND_ICONS -> resolvedIcon
            // Legacy entries only persisted the id, so keep it if it still maps to an icon of the pool.
            quickCommand.iconRes in COMMAND_ICONS -> quickCommand.iconRes
            else -> COMMAND_ICONS[0]
        }
        quickCommand.iconName = quickCommandIconName(quickCommand.iconRes)
    }

    /** Find an existing quick command by its display name, or null if there is none. */
    private fun findQuickCommandByName(name: String): QuickCommand? =
        mQuickCommands.firstOrNull { it.name == name }

    /** The fixed width shared by the quick commands panel and the floating terminal panel: 90%
     * of the screen width, capped so it does not stretch across large screens. */
    private fun getQuickCommandPanelWidth(): Int {
        val metrics = resources.displayMetrics
        return minOf((metrics.widthPixels * 0.9f).toInt(), (400 * metrics.density).toInt())
    }

    /** Height of the shared panel while it shows the list; the edit page shrink-wraps. */
    private fun getQuickCommandPanelListHeight(): Int =
        (resources.displayMetrics.heightPixels * 0.55f).toInt()

    /** Show the quick commands panel (opened from the terminal toolbar). The same panel also
     * hosts the add/edit form, so there is only ever one floating window on screen. */
    fun showQuickCommandsDialog() {
        loadQuickCommands()

        val existingHost = mQuickCommandsHost
        if (existingHost != null) {
            showQuickCommandsListPage()
            return
        }

        val state = QuickCommandsPanelState()
        mQuickCommandsPanelState = state

        val host = ComposeView(this)
        mQuickCommandsHost = host
        host.setContent {
            TermuxTheme {
                QuickCommandsPanel(
                    state = state,
                    commands = mQuickCommandsState.value,
                    restoreRows = buildRestoreRows(),
                    onClose = {
                        // On a subpage (add/edit form, the run/edit/delete actions or the restore
                        // list) the close button steps back to the list first; only the list page
                        // actually closes the panel.
                        if (state.page == QuickCommandsPage.LIST) hideQuickCommandsOverlay()
                        else showQuickCommandsListPage()
                    },
                    onAdd = { showQuickCommandEditPage(-1) },
                    onRestorePage = { showQuickCommandRestorePage() },
                    onRun = { command ->
                        hideQuickCommandsOverlay()
                        runQuickCommand(command)
                    },
                    onEdit = { position -> showQuickCommandEditPage(position) },
                    onShowActions = { position -> showQuickCommandActionsPage(position) },
                    onDelete = { position -> showQuickCommandDeleteDialog(position) },
                    onToggleFloating = { command ->
                        command.useFloatingTerminal = !command.useFloatingTerminal
                        saveQuickCommands()
                    },
                    onToggleNotify = { command ->
                        command.notifyResult = !command.notifyResult
                        saveQuickCommands()
                    },
                    onSave = { saveQuickCommandFromEditPage() },
                    onNameChange = { state.nameInput = it },
                    onCommandChange = { state.commandInput = it },
                    onToggleRestoreRow = { index ->
                        val checked = mQuickCommandRestoreChecked.value.toMutableSet()
                        if (!checked.add(index)) checked.remove(index)
                        mQuickCommandRestoreChecked.value = checked
                    },
                    onToggleRestoreAll = {
                        val selectable = restoreSelectableIndexes()
                        val allSelected = selectable.isNotEmpty() &&
                            selectable.all { it in mQuickCommandRestoreChecked.value }
                        mQuickCommandRestoreChecked.value = if (allSelected) emptySet() else selectable.toSet()
                    },
                    onRestoreSelected = { restoreSelectedQuickCommands() },
                    onCancelPage = { showQuickCommandsListPage() },
                    onDrag = { dx, dy -> translateQuickCommandsHost(dx, dy) },
                )
            }
        }

        // The panel is centered in the content view with a fixed size while it shows the list, and
        // shrink-wraps its height on the other pages.
        val content = findViewById<ViewGroup>(android.R.id.content)
        val params = FrameLayout.LayoutParams(getQuickCommandPanelWidth(), getQuickCommandPanelListHeight())
        params.gravity = Gravity.CENTER
        // Keep a little breathing room above the bottom edge / navigation bar.
        params.bottomMargin = (24 * resources.displayMetrics.density).toInt()
        content.addView(host, params)

        showQuickCommandsListPage()
    }

    /** Switch the shared panel to its list page (also used when leaving another page). */
    private fun showQuickCommandsListPage() {
        val state = mQuickCommandsPanelState ?: return
        hideQuickCommandKeyboard()

        state.page = QuickCommandsPage.LIST
        state.title = getString(R.string.title_quick_commands)
        state.editing = null
        state.actionPosition = -1
        mEditingQuickCommand = null
        mQuickCommandActionPosition = -1
        mQuickCommandRestoreChecked.value = emptySet()

        setQuickCommandsPanelHeight(getQuickCommandPanelListHeight())
    }

    /** Switch the shared panel to its add/edit page, keeping the same window and position. */
    private fun showQuickCommandEditPage(position: Int) {
        if (mQuickCommandsHost == null) showQuickCommandsDialog()
        val state = mQuickCommandsPanelState ?: return

        val isNew = position < 0 || position >= mQuickCommands.size
        val editing = if (isNew) null else mQuickCommands[position]
        mEditingQuickCommand = editing
        state.editing = editing
        state.actionPosition = -1
        mQuickCommandActionPosition = -1

        state.page = QuickCommandsPage.EDIT
        state.title = getString(
            if (isNew) R.string.title_add_quick_command else R.string.title_edit_quick_command
        )
        state.nameInput = editing?.name ?: ""
        state.commandInput = editing?.command ?: ""

        setQuickCommandsPanelHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
        hideQuickCommandKeyboard()
    }

    /** Show the run/edit/delete actions for the tapped command inside the shared panel. */
    private fun showQuickCommandActionsPage(position: Int) {
        if (position < 0 || position >= mQuickCommands.size) return
        if (mQuickCommandsHost == null) showQuickCommandsDialog()
        val state = mQuickCommandsPanelState ?: return

        mQuickCommandActionPosition = position
        state.actionPosition = position
        state.editing = null
        mEditingQuickCommand = null
        hideQuickCommandKeyboard()

        state.page = QuickCommandsPage.ACTIONS
        state.title = mQuickCommands[position].name

        setQuickCommandsPanelHeight(ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /** Show the page that lists the built-in default commands. */
    private fun showQuickCommandRestorePage() {
        val state = mQuickCommandsPanelState ?: return
        hideQuickCommandKeyboard()

        state.page = QuickCommandsPage.RESTORE
        state.title = getString(R.string.title_restore_quick_commands)
        state.editing = null
        state.actionPosition = -1
        mEditingQuickCommand = null
        mQuickCommandActionPosition = -1
        mQuickCommandRestoreChecked.value = emptySet()

        setQuickCommandsPanelHeight(getQuickCommandPanelListHeight())
    }

    /** The restore page rows: restorable defaults first, the ones already in the list last. */
    private fun buildRestoreRows(): List<RestoreRow> {
        val checked = mQuickCommandRestoreChecked.value
        val rows = DEFAULT_COMMANDS.indices.map { index ->
            val name = DEFAULT_COMMANDS[index][0]
            val alreadyInList = findQuickCommandByName(name) != null
            RestoreRow(
                index = index,
                name = name,
                command = DEFAULT_COMMANDS[index][1],
                alreadyInList = alreadyInList,
                checked = alreadyInList || index in checked,
            )
        }
        return rows.sortedBy { it.alreadyInList }
    }

    /** Indexes of the restore rows that can still be selected. */
    private fun restoreSelectableIndexes(): List<Int> = DEFAULT_COMMANDS.indices.filter { index ->
        findQuickCommandByName(DEFAULT_COMMANDS[index][0]) == null
    }

    /** Add the checked defaults that are not in the list yet and return to the list page. */
    private fun restoreSelectedQuickCommands() {
        val checked = mQuickCommandRestoreChecked.value
        for (index in checked) {
            if (index < 0 || index >= DEFAULT_COMMANDS.size) continue
            val name = DEFAULT_COMMANDS[index][0]
            // Never overwrite or duplicate an entry the user already has.
            if (findQuickCommandByName(name) != null) continue
            mQuickCommands.add(
                QuickCommand(name, DEFAULT_COMMANDS[index][1], COMMAND_ICONS[index % COMMAND_ICONS.size])
            )
        }
        saveQuickCommands()
        showQuickCommandsListPage()
    }

    /** Save the values entered on the panel's add/edit page and return to the list page. */
    private fun saveQuickCommandFromEditPage() {
        val state = mQuickCommandsPanelState ?: return
        val name = state.nameInput.trim()
        val command = state.commandInput.trim()
        if (name.isEmpty() || command.isEmpty()) {
            Toast.makeText(this, R.string.msg_quick_command_required, Toast.LENGTH_SHORT).show()
            return
        }
        val editing = mEditingQuickCommand
        if (editing == null) {
            mQuickCommands.add(QuickCommand(name, command, nextQuickCommandIcon()))
        } else {
            editing.name = name
            editing.command = command
        }
        saveQuickCommands()
        showQuickCommandsListPage()
    }

    /** Confirm and delete the quick command at the given position. */
    private fun showQuickCommandDeleteDialog(position: Int) {
        if (position < 0 || position >= mQuickCommands.size) return
        val quickCommand = mQuickCommands[position]
        AlertDialog.Builder(this)
            .setTitle(R.string.title_confirm_kill_process)
            .setMessage(getString(R.string.msg_confirm_delete_quick_command, quickCommand.name))
            .setPositiveButton(android.R.string.yes) { _, _ ->
                mQuickCommands.removeAt(position)
                saveQuickCommands()
                // The deleted command no longer exists: leave its action page.
                if (mQuickCommandsPanelState?.page == QuickCommandsPage.ACTIONS) showQuickCommandsListPage()
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    /** Resize the shared panel. The panel is centered, so a height change would re-center it and
     * move the top edge; the visual top edge is captured before the resize and restored after,
     * so switching pages only grows/shrinks downwards. */
    private fun setQuickCommandsPanelHeight(height: Int) {
        val host = mQuickCommandsHost ?: return
        val params = host.layoutParams ?: return
        if (params.height == height) return

        val anchorTop = host.top + host.translationY
        params.height = height
        host.layoutParams = params
        host.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int,
            ) {
                v.removeOnLayoutChangeListener(this)
                if (v !== mQuickCommandsHost) return
                // Centered gravity moves the top with the height; translate back so the top edge
                // sits exactly where it was.
                v.translationY = anchorTop - v.top
                clampOverlayTranslation(v)
            }
        })
    }

    /** Hide the soft keyboard for the panel's inputs, e.g. when leaving the edit page. */
    private fun hideQuickCommandKeyboard() {
        val host = mQuickCommandsHost ?: return
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val focused = host.findFocus() ?: return
        imm.hideSoftInputFromWindow(focused.windowToken, 0)
        focused.clearFocus()
    }

    /** Close the quick commands panel, if shown. */
    private fun hideQuickCommandsOverlay() {
        val host = mQuickCommandsHost ?: return
        mQuickCommandsHost = null
        mQuickCommandsPanelState = null
        mEditingQuickCommand = null
        mQuickCommandActionPosition = -1
        mQuickCommandRestoreChecked.value = emptySet()
        (host.parent as? ViewGroup)?.removeView(host)
    }

    // ---------------------------------------------------------------- quick command execution

    /** Run a quick command, either in the floating terminal panel or as a background task. */
    private fun runQuickCommand(quickCommand: QuickCommand) {
        getDrawer()?.closeDrawers()
        val command = quickCommand.command

        val service = mTermuxService
        if (service == null) {
            // Service not bound yet; fall back to typing the command into the current session.
            val session = getCurrentSession()
            if (session != null && session.isRunning) session.write(command + "\r")
            return
        }

        // Per-command setting: when the floating terminal is disabled, run the command as a
        // background task instead of showing the floating terminal panel.
        if (!quickCommand.useFloatingTerminal) {
            val workingDirectory = getCurrentSession()?.cwd

            val loading = ProgressDialog(this)
            loading.setMessage(getString(R.string.msg_quick_command_running, quickCommand.name))
            loading.setCanceledOnTouchOutside(true)
            loading.show()

            val executionCommand = ExecutionCommand(
                TermuxShellManager.getNextShellId(),
                TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh",
                arrayOf("-c", command), null, workingDirectory,
                ExecutionCommand.Runner.APP_SHELL.getName(), false
            )
            executionCommand.shellName = quickCommand.name

            // Register the result callback BEFORE starting the task: a fast command can finish
            // and fire the callback before createTermuxTask() returns.
            service.registerAppShellResultCallback(executionCommand.id!!) { _, exitCode, stdout, stderr ->
                loading.dismiss()
                if (!quickCommand.notifyResult) return@registerAppShellResultCallback

                var output = stdout?.trim().orEmpty()
                if (output.isEmpty() && stderr != null) output = stderr.trim()
                val message = if (exitCode != null && exitCode == 0) {
                    getString(R.string.msg_quick_command_success, quickCommand.name) +
                        if (output.isEmpty()) "" else "：\n$output"
                } else {
                    getString(
                        R.string.msg_quick_command_failed,
                        quickCommand.name,
                        exitCode ?: -1,
                        if (output.isEmpty()) "-" else output
                    )
                }
                // Show the result as a toast instead of a modal dialog: a dialog would cover
                // whichever session window is displayed and need an extra keypress to dismiss.
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }

            val task = service.createTermuxTask(executionCommand)
            if (task == null) loading.dismiss()
            return
        }

        showFloatingTerminalPanel(command)
    }

    // ---------------------------------------------------------------- floating terminal

    /** Show (or reuse) the floating terminal panel and run the command in a dedicated session
     * attached to its TerminalView. */
    private fun showFloatingTerminalPanel(command: String) {
        val panelWidth = getQuickCommandPanelWidth()
        val panelHeight = resources.displayMetrics.heightPixels / 2

        mFloatingPanelCommand = command

        val state = mFloatingTerminalState ?: FloatingTerminalState().also { mFloatingTerminalState = it }
        state.command = command
        state.title = getShortDisplayPath(getCurrentSession()?.cwd)

        if (mFloatingPanelHost == null) {
            val host = ComposeView(this)
            mFloatingPanelHost = host
            host.setContent {
                TermuxTheme {
                    FloatingTerminalPanel(
                        title = state.title,
                        onClose = { hideFloatingTerminalPanel() },
                        onRerun = { rerunFloatingPanelCommand() },
                        onMinimize = { minimizeFloatingTerminalPanel() },
                        onDrag = { dx, dy -> translateFloatingPanelHost(dx, dy) },
                        onTerminalViewCreated = { terminalView ->
                            mFloatingTerminalView = terminalView
                            terminalView.setTerminalViewClient(floatingPanelTerminalViewClient())
                            // The view is created asynchronously by Compose, so a command that was
                            // requested before it existed is started here.
                            mPendingFloatingCommand?.let { pending ->
                                mPendingFloatingCommand = null
                                startFloatingPanelCommand(pending)
                            }
                        },
                    )
                }
            }

            val content = findViewById<ViewGroup>(android.R.id.content)
            val params = FrameLayout.LayoutParams(panelWidth, panelHeight)
            params.gravity = Gravity.CENTER
            content.addView(host, params)
        }

        removeFloatingTerminalBubble()
        mFloatingPanelHost?.visibility = View.VISIBLE

        if (mFloatingTerminalView == null) {
            mPendingFloatingCommand = command
        } else {
            startFloatingPanelCommand(command)
        }
    }

    /** Shorten a working directory for display: null/empty and the Termux home become "~", and
     * paths under home keep their "~/..." tail so long prefixes do not crowd the title bar. */
    private fun getShortDisplayPath(path: String?): String {
        val home = TermuxConstants.TERMUX_HOME_DIR_PATH
        if (path.isNullOrEmpty()) return "~"
        if (path == home) return "~"
        if (path.startsWith("$home/")) return "~" + path.substring(home.length)
        return path
    }

    /** Create a fresh session for the given command and attach it to the panel's TerminalView. */
    private fun startFloatingPanelCommand(command: String) {
        val service = mTermuxService ?: return
        val terminalView = mFloatingTerminalView ?: return
        val preferences = mPreferences ?: return

        // The renderer starts out null, so initialize it with the user's font size before
        // attaching a session, otherwise updateSize() would crash reading mRenderer.mFontWidth.
        terminalView.setTextSize(preferences.fontSize)

        val workingDirectory = getCurrentSession()?.cwd

        // Create a login shell session without going through the `login` binary (and its motd):
        // pass the shell binary explicitly with the login flag, so argv[0] gets the "-" prefix
        // and ~/.profile is loaded, but no startup banner is printed. The command is passed with
        // `-c` instead of being typed into an interactive shell (and without appending `exit`),
        // so the session ends on its own once the command finishes.
        val executionCommand = ExecutionCommand(
            TermuxShellManager.getNextShellId(),
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh", arrayOf("-c", command), null,
            workingDirectory, ExecutionCommand.Runner.TERMINAL_SESSION.getName(), false
        )
        executionCommand.shellName = getString(R.string.title_quick_command_session_name)
        executionCommand.isLoginShellCommand = true
        executionCommand.isFloatingPanelSession = true

        val termuxSession = service.createTermuxSession(executionCommand) ?: return
        val session = termuxSession.terminalSession ?: return

        mFloatingTerminalState?.title = getShortDisplayPath(workingDirectory)

        // Use a standalone client so the panel session shares nothing with the main sessions
        // (no tabs, no session list, no shared callbacks).
        session.updateTerminalSessionClient(FloatingPanelSessionClient())
        terminalView.attachSession(session)
    }

    /** Re-run the last command in the panel with a fresh session. */
    private fun rerunFloatingPanelCommand() {
        val command = mFloatingPanelCommand ?: return
        if (mFloatingPanelHost == null) return
        finishFloatingPanelSession()
        startFloatingPanelCommand(command)
    }

    /** Hide the floating terminal panel and finish its current session if still running. */
    private fun hideFloatingTerminalPanel() {
        removeFloatingTerminalBubble()
        val host = mFloatingPanelHost ?: return
        mFloatingPanelHost = null
        finishFloatingPanelSession()
        mFloatingTerminalView = null
        (host.parent as? ViewGroup)?.removeView(host)
    }

    /** Shrink the floating terminal panel into a bubble docked to the nearest screen edge,
     * remembering where the panel was so it can return to the same anchored position. */
    private fun minimizeFloatingTerminalPanel() {
        val host = mFloatingPanelHost ?: return
        mFloatingTerminalAnchorTop = host.top + host.translationY
        host.visibility = View.GONE
        showFloatingTerminalBubble()
    }

    /** Restore the floating terminal panel from its minimized bubble. */
    private fun restoreFloatingTerminalPanel() {
        removeFloatingTerminalBubble()
        val host = mFloatingPanelHost ?: return
        host.visibility = View.VISIBLE
        host.post { applyFloatingTerminalAnchor() }
    }

    /** Put the panel's top edge back at the anchored position, clamped to the visible area. */
    private fun applyFloatingTerminalAnchor() {
        val host = mFloatingPanelHost ?: return
        val anchorTop = mFloatingTerminalAnchorTop
        if (!anchorTop.isNaN()) host.translationY = anchorTop - host.top
        clampOverlayTranslation(host)
    }

    /** Show the circular bubble, docked to whichever screen edge the panel was closest to and
     * vertically centered on the panel's anchored position. */
    private fun showFloatingTerminalBubble() {
        if (mFloatingBubbleHost != null) return
        val panel = mFloatingPanelHost ?: return
        val content = findViewById<ViewGroup>(android.R.id.content) ?: return

        val density = resources.displayMetrics.density
        val size = (48 * density).toInt()
        val margin = (8 * density).toInt()
        val parentWidth = content.width
        val parentHeight = content.height
        val panelHeight = panel.height

        val insetTop = getContentTopInset()
        val insetBottom = getContentBottomInset()
        val minTopMargin = insetTop + margin
        val maxTopMargin = parentHeight - insetBottom - size - margin

        val topMargin: Int
        val dockRight: Boolean
        if (!mFloatingTerminalBubbleTop.isNaN()) {
            // Re-use the edge and vertical position the bubble was last docked at.
            dockRight = mFloatingTerminalBubbleDockRight
            topMargin = mFloatingTerminalBubbleTop.roundToInt().coerceIn(minTopMargin, maxTopMargin)
        } else {
            val panelWidth = panel.width
            val panelCenterX = panel.left + panel.translationX + panelWidth / 2f
            val anchorTop = if (mFloatingTerminalAnchorTop.isNaN())
                (insetTop + parentHeight - insetBottom - panelHeight) / 2f
            else
                mFloatingTerminalAnchorTop
            topMargin = (anchorTop + panelHeight / 2f - size / 2f)
                .roundToInt().coerceIn(minTopMargin, maxTopMargin)
            dockRight = panelCenterX > parentWidth / 2f
        }
        // Keep the panel's restore position in sync with the bubble it will be restored from.
        mFloatingTerminalAnchorTop = topMargin + size / 2f - panelHeight / 2f

        val params = FrameLayout.LayoutParams(size, size)
        params.gravity = Gravity.TOP or (if (dockRight) Gravity.END else Gravity.START)
        if (dockRight) params.rightMargin = margin else params.leftMargin = margin
        params.topMargin = topMargin

        val bubbleHost = ComposeView(this)
        mFloatingBubbleHost = bubbleHost
        bubbleHost.setContent {
            TermuxTheme {
                FloatingTerminalBubble(
                    onRestore = { restoreFloatingTerminalPanel() },
                    onDrag = { dx, dy -> dragFloatingTerminalBubble(bubbleHost, dx, dy) },
                    onDragEnd = { snapFloatingTerminalBubble(bubbleHost) },
                )
            }
        }
        content.addView(bubbleHost, params)
    }

    /** Keep a dragged bubble fully inside its parent and out of the system bars. */
    private fun dragFloatingTerminalBubble(bubble: View, dx: Float, dy: Float) {
        bubble.translationX += dx
        bubble.translationY += dy

        val parent = bubble.parent as? View ?: return
        if (parent.width == 0 || parent.height == 0) return
        val minX = -bubble.left.toFloat()
        val maxX = (parent.width - bubble.width - bubble.left).toFloat()
        val minY = (getContentTopInset() - bubble.top).toFloat()
        val maxY = (parent.height - getContentBottomInset() - bubble.height - bubble.top).toFloat()

        bubble.translationX = bubble.translationX.coerceIn(minX, maxX)
        bubble.translationY = bubble.translationY.coerceIn(minY, maxY)
    }

    /** After a drag, snap the bubble to the nearest screen edge and move the panel's anchor to
     * match, so restoring the panel opens it next to where the bubble was dropped. */
    private fun snapFloatingTerminalBubble(bubble: View) {
        val parent = bubble.parent as? View ?: return
        val size = bubble.width
        val margin = (8 * resources.displayMetrics.density).toInt()
        val parentWidth = parent.width
        val parentHeight = parent.height

        val left = bubble.left + bubble.translationX
        val top = bubble.top + bubble.translationY
        val dockLeft = left + size / 2f <= parentWidth / 2f
        val finalLeft = if (dockLeft) margin else parentWidth - size - margin
        // The bubble must stay below the status bar and above the navigation bar.
        val finalTop = maxOf(
            getContentTopInset() + margin,
            minOf(top.roundToInt(), parentHeight - getContentBottomInset() - size - margin)
        )

        val params = FrameLayout.LayoutParams(size, size)
        params.gravity = Gravity.TOP or Gravity.START
        params.leftMargin = finalLeft
        params.topMargin = finalTop
        bubble.layoutParams = params
        bubble.translationX = 0f
        bubble.translationY = 0f

        val panelHeight = mFloatingPanelHost?.height ?: 0
        mFloatingTerminalAnchorTop = finalTop + size / 2f - panelHeight / 2f
        // Remember the docked position so the next minimize puts the bubble back here.
        mFloatingTerminalBubbleDockRight = !dockLeft
        mFloatingTerminalBubbleTop = finalTop.toFloat()
    }

    /** Remove the minimized bubble, if present. */
    private fun removeFloatingTerminalBubble() {
        val host = mFloatingBubbleHost ?: return
        mFloatingBubbleHost = null
        (host.parent as? ViewGroup)?.removeView(host)
    }

    /** Finish the terminal session currently attached to the panel's view, if any. */
    private fun finishFloatingPanelSession() {
        val session = mFloatingTerminalView?.currentSession ?: return
        if (session.isRunning) session.finishIfRunning()
    }

    /** The TerminalViewClient the floating panel's TerminalView needs; TerminalView requires one
     * (e.g. onEmulatorSet is invoked right after attachSession) or it crashes. */
    private fun floatingPanelTerminalViewClient(): TerminalViewClient =
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            override fun onSingleTapUp(e: MotionEvent) {}
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            override fun shouldEnforceCharBasedInput(): Boolean = false
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            override fun isTerminalViewSelected(): Boolean = false
            override fun copyModeChanged(copyMode: Boolean) {}
            override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
            override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
            override fun onLongPress(event: MotionEvent): Boolean = false
            override fun readControlKey(): Boolean = false
            override fun readAltKey(): Boolean = false
            override fun readShiftKey(): Boolean = false
            override fun readFnKey(): Boolean = false
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
            override fun onEmulatorSet() {}
            override fun logError(tag: String, message: String) = Logger.logError(tag, message)
            override fun logWarn(tag: String, message: String) = Logger.logWarn(tag, message)
            override fun logInfo(tag: String, message: String) = Logger.logInfo(tag, message)
            override fun logDebug(tag: String, message: String) = Logger.logDebug(tag, message)
            override fun logVerbose(tag: String, message: String) = Logger.logVerbose(tag, message)
            override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) =
                Logger.logStackTraceWithMessage(tag, message, e)
            override fun logStackTrace(tag: String, e: Exception) = Logger.logStackTrace(tag, e)
        }

    /**
     * TerminalSessionClient for the session shown in the floating terminal panel. Fully
     * standalone: it only refreshes the panel's TerminalView and removes the finished session
     * from the service, so the panel session shares nothing with the main sessions.
     */
    private inner class FloatingPanelSessionClient : TermuxTerminalSessionClientBase() {

        override fun onTextChanged(changedSession: TerminalSession) {
            val terminalView = mFloatingTerminalView ?: return
            if (terminalView.currentSession === changedSession) terminalView.onScreenUpdated()
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            // 任务执行完毕后不关闭窗口：只从中断（移除）会话，悬浮面板继续显示输出。
            runOnUiThread {
                // Not delegated to the activity client, so remove the finished session from the
                // service's session list here to avoid leaving it behind.
                mTermuxService?.removeTermuxSession(finishedSession)
            }
        }
    }

    // ---------------------------------------------------------------- overlay geometry

    /** Top inset occupied by the status bar, in the coordinate space of the content view that
     * hosts the floating overlays. The content view normally spans the whole window, including
     * the area behind the status bar, so its raw bounds cannot keep views out of it. */
    private fun getContentTopInset(): Int {
        val content = findViewById<View>(android.R.id.content) ?: return mSystemBarInsetTop
        val rootInsets = content.rootWindowInsets ?: return mSystemBarInsetTop
        val windowInsetTop = WindowInsetsCompat.toWindowInsetsCompat(rootInsets)
            .getInsets(WindowInsetsCompat.Type.statusBars()).top
        if (windowInsetTop <= 0) return 0
        val location = IntArray(2)
        content.getLocationInWindow(location)
        return maxOf(0, windowInsetTop - location[1])
    }

    /** Bottom inset occupied by the navigation bar, in the coordinate space of the content view
     * that hosts the floating overlays. */
    private fun getContentBottomInset(): Int {
        val content = findViewById<View>(android.R.id.content) ?: return mSystemBarInsetBottom
        val rootInsets = content.rootWindowInsets ?: return mSystemBarInsetBottom
        val windowInsetBottom = WindowInsetsCompat.toWindowInsetsCompat(rootInsets)
            .getInsets(WindowInsetsCompat.Type.systemBars()).bottom
        if (windowInsetBottom <= 0) return 0
        val location = IntArray(2)
        content.getLocationInWindow(location)
        val windowHeight = content.rootView.height
        if (windowHeight <= 0) return mSystemBarInsetBottom
        val contentBottom = location[1] + content.height
        return maxOf(0, windowInsetBottom - (windowHeight - contentBottom))
    }

    /** Clamp an overlay's translation so the whole view stays inside its parent and out of the
     * system bars. The content view the overlays are attached to can extend behind the status
     * and navigation bars, so the raw parent bounds alone would let them be dragged into the
     * status bar. */
    private fun clampOverlayTranslation(overlay: View) {
        val parent = overlay.parent as? View ?: return
        if (parent.width == 0 || parent.height == 0) return

        var minX = -overlay.left.toFloat()
        var maxX = (parent.width - overlay.width - overlay.left).toFloat()
        var minY = (getContentTopInset() - overlay.top).toFloat()
        var maxY = (parent.height - getContentBottomInset() - overlay.height - overlay.top).toFloat()

        // If the safe area is smaller than the overlay, center it inside the safe area instead.
        if (minX > maxX) {
            val centerX = (minX + maxX) / 2f
            minX = centerX
            maxX = centerX
        }
        if (minY > maxY) {
            val centerY = (minY + maxY) / 2f
            minY = centerY
            maxY = centerY
        }

        overlay.translationX = overlay.translationX.coerceIn(minX, maxX)
        overlay.translationY = overlay.translationY.coerceIn(minY, maxY)
    }

    /** Drag the quick commands panel by its title bar. */
    private fun translateQuickCommandsHost(dx: Float, dy: Float) {
        val host = mQuickCommandsHost ?: return
        host.translationX += dx
        host.translationY += dy
        clampOverlayTranslation(host)
    }

    /** Drag the floating terminal panel by its title bar. */
    private fun translateFloatingPanelHost(dx: Float, dy: Float) {
        val host = mFloatingPanelHost ?: return
        host.translationX += dx
        host.translationY += dy
        clampOverlayTranslation(host)
    }

    // ---------------------------------------------------------------- extra key symbols

    /** The symbols the user added to the terminal toolbar, in display order. */
    fun getExtraKeySymbols(): MutableList<ExtraKeySymbol> {
        val symbols = mutableListOf<ExtraKeySymbol>()
        val json = activityPreferences().getString(KEY_EXTRA_KEY_SYMBOLS, null) ?: return symbols
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                ExtraKeySymbol.fromJson(array.opt(i))?.let { symbols.add(it) }
            }
        } catch (e: JSONException) {
            Logger.logError(LOG_TAG, "Failed to parse saved extra key symbols: " + e.message)
        }
        return symbols
    }

    /** Show a dialog that lets the user add a symbol to the terminal toolbar. */
    fun showAddExtraKeySymbolDialog() {
        showExtraKeySymbolDialog(null)
    }

    /** Show a dialog that lets the user edit a symbol already added to the terminal toolbar, or
     * delete it. This is triggered by long pressing the symbol button. */
    fun showEditExtraKeySymbolDialog(symbol: String) {
        showExtraKeySymbolDialog(symbol)
    }

    /** Show the dialog that adds a symbol to the terminal toolbar, or edits an existing one. */
    private fun showExtraKeySymbolDialog(editSymbol: String?) {
        val density = resources.displayMetrics.density
        val horizontalPadding = (20 * density).toInt()

        val editing = editSymbol != null
        val existing: ExtraKeySymbol
        if (editing) {
            existing = getExtraKeySymbols().firstOrNull { it.symbol == editSymbol } ?: return
        } else {
            existing = ExtraKeySymbol("", 0, 0)
        }

        val symbolInput = EditText(this).apply {
            setHint(R.string.hint_extra_key_symbol)
            setSingleLine(true)
            if (editing) setText(existing.symbol)
        }

        val aliasInput = EditText(this).apply {
            setHint(R.string.hint_extra_key_alias)
            setSingleLine(true)
            if (editing && existing.alias != null) setText(existing.alias)
        }

        // A new symbol defaults to the first cell; an existing symbol keeps its saved position.
        val defaultRow = 1
        val defaultColumn = 1

        val rowInput = createExtraKeyPositionInput(
            R.string.hint_extra_key_row, buildExtraKeyPositionOptions(getExtraKeyRowCount())
        )
        rowInput.setText(if (editing && existing.row > 0) existing.row.toString() else defaultRow.toString())

        val columnInput = createExtraKeyPositionInput(
            R.string.hint_extra_key_column, buildExtraKeyPositionOptions(getExtraKeyColumnCount())
        )
        columnInput.setText(
            if (editing && existing.column > 0) existing.column.toString() else defaultColumn.toString()
        )

        val positionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(rowInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(columnInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(horizontalPadding, (8 * density).toInt(), horizontalPadding, 0)
            addView(symbolInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(aliasInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(positionRow, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(
                if (editing) R.string.title_edit_extra_key_symbol else R.string.title_add_extra_key_symbol
            )
            .setView(container)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(
                if (editing) R.string.action_save_extra_key_symbol else R.string.action_add_extra_key_symbol
            ) { _, _ ->
                val symbol = symbolInput.text.toString().trim()
                if (symbol.isEmpty()) return@setPositiveButton
                val alias = aliasInput.text.toString().trim()
                val row = parseExtraKeyPosition(rowInput)
                val column = parseExtraKeyPosition(columnInput)
                if (editing) {
                    updateExtraKeySymbol(existing.symbol, symbol, alias, row, column)
                } else {
                    addExtraKeySymbol(symbol, alias, row, column)
                }
            }

        if (editing) {
            // Deleting the symbol is offered from the same dialog, so a long press is enough to
            // manage a symbol without any other entry point in the toolbar.
            builder.setNeutralButton(R.string.action_delete_extra_key_symbol) { _, _ ->
                deleteExtraKeySymbol(existing.symbol)
            }
        }

        val dialog = builder.create()
        dialog.setOnShowListener {
            symbolInput.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
        dialog.show()
    }

    /** Parse an optional 1-based row/column; an empty or invalid value means append automatically. */
    private fun parseExtraKeyPosition(input: EditText): Int = try {
        maxOf(0, input.text.toString().trim().toInt())
    } catch (e: NumberFormatException) {
        // An empty field means no fixed position, so the symbol is appended automatically.
        0
    }

    /** Create an input that accepts both a typed row/column and one picked from a dropdown. */
    private fun createExtraKeyPositionInput(hintResId: Int, options: List<String>): AutoCompleteTextView {
        val input = AutoCompleteTextView(this)
        input.setHint(hintResId)
        input.setSingleLine(true)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.gravity = Gravity.CENTER
        input.threshold = 0
        input.setAdapter(createExtraKeyPositionAdapter(options))

        val arrow = createExtraKeyDropdownIcon()
        if (arrow != null) {
            input.setCompoundDrawablesWithIntrinsicBounds(null, null, arrow, null)

            // The trailing arrow shrinks the text area, which would make centred text look shifted
            // to the left, so the same width is added as leading padding to keep it centred.
            val endPadding = input.paddingEnd
            input.setPaddingRelative(
                endPadding + arrow.intrinsicWidth,
                input.paddingTop, endPadding, input.paddingBottom
            )

            // Showing the dropdown from an OnClickListener would consume the touch and stop the
            // field from being editable, so it opens on focus and from the arrow instead.
            input.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP &&
                    event.x >= v.width - v.paddingEnd - arrow.bounds.width()
                ) {
                    input.showDropDown()
                    true
                } else {
                    false
                }
            }
        }

        input.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) input.showDropDown()
        }
        return input
    }

    /** Create the dropdown arrow shown at the end of a row/column input, tinted with the theme
     * text colour so that it stays visible in both light and dark themes. */
    private fun createExtraKeyDropdownIcon(): Drawable? {
        val icon = AppCompatResources.getDrawable(this, R.drawable.ic_extra_key_dropdown) ?: return null
        val tinted = DrawableCompat.wrap(icon.mutate())
        DrawableCompat.setTint(tinted, ThemeUtils.getTextColorSecondary(this))
        return tinted
    }

    /** Create the adapter for a row/column input. Every option is always offered, including the
     * ones that do not match what has been typed, because the field is not a search box. */
    private fun createExtraKeyPositionAdapter(options: List<String>): ArrayAdapter<String> =
        object : ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, options) {
            override fun getFilter(): Filter = object : Filter() {
                override fun performFiltering(constraint: CharSequence?): FilterResults =
                    FilterResults().apply {
                        values = options
                        count = options.size
                    }

                override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                    notifyDataSetChanged()
                }
            }
        }

    /** The dropdown options for a row/column: every 1-based index, plus one more than currently
     * exists so a symbol can be placed in a new row/column. */
    private fun buildExtraKeyPositionOptions(currentCount: Int): List<String> {
        val max = maxOf(currentCount + 1, 1)
        return (1..max).map { it.toString() }
    }

    /** The number of rows currently in the toolbar grid. */
    private fun getExtraKeyRowCount(): Int = mTermuxTerminalExtraKeys?.rowCount ?: 1

    /** The number of columns currently in the toolbar grid. */
    private fun getExtraKeyColumnCount(): Int = mTermuxTerminalExtraKeys?.columnCount ?: 1

    /** Persist a new toolbar symbol and refresh the toolbar. */
    private fun addExtraKeySymbol(symbol: String, alias: String?, row: Int, column: Int) {
        val symbols = getExtraKeySymbols()
        if (symbols.any { it.symbol == symbol }) {
            Toast.makeText(this, R.string.msg_extra_key_symbol_exists, Toast.LENGTH_SHORT).show()
            return
        }
        val hasPosition = row > 0 && column > 0
        symbols.add(ExtraKeySymbol(symbol, alias, if (hasPosition) row else 0, if (hasPosition) column else 0))
        saveExtraKeySymbols(symbols)
        reloadExtraKeys()
    }

    /** Replace a toolbar symbol with the edited one, keeping its position in the list so the
     * toolbar order does not change. */
    private fun updateExtraKeySymbol(oldSymbol: String, symbol: String, alias: String?, row: Int, column: Int) {
        val symbols = getExtraKeySymbols()
        val index = symbols.indexOfFirst { it.symbol == oldSymbol }
        if (index < 0) return

        if (symbols.withIndex().any { (i, existing) -> i != index && existing.symbol == symbol }) {
            Toast.makeText(this, R.string.msg_extra_key_symbol_exists, Toast.LENGTH_SHORT).show()
            return
        }

        val hasPosition = row > 0 && column > 0
        symbols[index] = ExtraKeySymbol(
            symbol, alias, if (hasPosition) row else 0, if (hasPosition) column else 0
        )
        saveExtraKeySymbols(symbols)
        reloadExtraKeys()
    }

    /** Remove a toolbar symbol and refresh the toolbar. */
    private fun deleteExtraKeySymbol(symbol: String) {
        val symbols = getExtraKeySymbols()
        val removed = symbols.removeAll { it.symbol == symbol }
        if (!removed) return
        saveExtraKeySymbols(symbols)
        reloadExtraKeys()
    }

    /** Persist the toolbar symbols as a JSON array. */
    private fun saveExtraKeySymbols(symbols: List<ExtraKeySymbol>) {
        val array = JSONArray()
        for (symbol in symbols) {
            try {
                array.put(symbol.toJson())
            } catch (e: JSONException) {
                Logger.logError(LOG_TAG, "Failed to save extra key symbol: " + e.message)
            }
        }
        activityPreferences().edit().putString(KEY_EXTRA_KEY_SYMBOLS, array.toString()).apply()
    }

    /** Rebuild the terminal toolbar after its extra keys changed. */
    private fun reloadExtraKeys() {
        val extraKeysView = mExtraKeysView ?: return
        val extraKeys = mTermuxTerminalExtraKeys ?: return
        extraKeys.reloadExtraKeysInfo()
        extraKeysView.reload(extraKeys.extraKeysInfo, mTerminalToolbarDefaultHeight)
        setTerminalToolbarHeight()
    }

    // ---------------------------------------------------------------- back, toasts and menus

    @SuppressLint("RtlHardcoded")
    override fun onBackPressed() {
        val drawer = getDrawer()
        if (drawer != null && drawer.isDrawerOpen(Gravity.LEFT)) {
            drawer.closeDrawers()
        } else if (mFloatingPanelHost != null) {
            // The panel is an in-app overlay: close it before leaving the activity.
            hideFloatingTerminalPanel()
        } else if (mQuickCommandsHost != null &&
            (mQuickCommandsPanelState?.page == QuickCommandsPage.EDIT ||
                mQuickCommandsPanelState?.page == QuickCommandsPage.ACTIONS)
        ) {
            // Inside the shared panel: leave the edit/actions page first, keep the panel open.
            showQuickCommandsListPage()
        } else if (mQuickCommandsHost != null) {
            hideQuickCommandsOverlay()
        } else {
            finishActivityIfNotFinishing()
        }
    }

    fun finishActivityIfNotFinishing() {
        // Prevent duplicate calls to finish() if called from multiple places.
        if (!isFinishing) finish()
    }

    /** Show a toast and dismiss the last one if still visible. */
    fun showToast(text: String?, longDuration: Boolean) {
        if (text.isNullOrEmpty()) return
        mLastToast?.cancel()
        mLastToast = Toast.makeText(this, text, if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT)
            .apply { setGravity(Gravity.TOP, 0, 0) }
        mLastToast?.show()
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenu.ContextMenuInfo?) {
        val currentSession = getCurrentSession() ?: return
        val terminalView = mTerminalView ?: return
        val preferences = mPreferences ?: return

        val autoFillEnabled = terminalView.isAutoFillEnabled

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url)
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript)
        if (!DataUtils.isNullOrEmpty(terminalView.storedSelectedText))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text)
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username)
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password)
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal)
        menu.add(
            Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE,
            resources.getString(R.string.action_kill_process, currentSession.pid)
        ).isEnabled = currentSession.isRunning
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal)
        menu.add(
            Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE,
            R.string.action_toggle_keep_screen_on
        ).isCheckable = true
        menu.findItem(CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON)?.isChecked = preferences.shouldKeepScreenOn()
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help)
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings)
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue)
    }

    /** Hook the system menu to show the terminal context menu instead. */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        mTerminalView?.showContextMenu()
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val session = getCurrentSession()
        val terminalView = mTerminalView

        return when (item.itemId) {
            CONTEXT_MENU_SELECT_URL_ID -> {
                mTermuxTerminalViewClient?.showUrlSelection()
                true
            }

            CONTEXT_MENU_SHARE_TRANSCRIPT_ID -> {
                mTermuxTerminalViewClient?.shareSessionTranscript()
                true
            }

            CONTEXT_MENU_SHARE_SELECTED_TEXT -> {
                mTermuxTerminalViewClient?.shareSelectedText()
                true
            }

            CONTEXT_MENU_AUTOFILL_USERNAME -> {
                terminalView?.requestAutoFillUsername()
                true
            }

            CONTEXT_MENU_AUTOFILL_PASSWORD -> {
                terminalView?.requestAutoFillPassword()
                true
            }

            CONTEXT_MENU_RESET_TERMINAL_ID -> {
                onResetTerminalSession(session)
                true
            }

            CONTEXT_MENU_KILL_PROCESS_ID -> {
                showKillSessionDialog(session)
                true
            }

            CONTEXT_MENU_STYLING_ID -> {
                showStylingDialog()
                true
            }

            CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON -> {
                toggleKeepScreenOn()
                true
            }

            CONTEXT_MENU_HELP_ID -> {
                ActivityUtils.startActivity(this, Intent(this, HelpActivity::class.java))
                true
            }

            CONTEXT_MENU_SETTINGS_ID -> {
                ActivityUtils.startActivity(this, Intent(this, SettingsActivity::class.java))
                true
            }

            CONTEXT_MENU_REPORT_ID -> {
                mTermuxTerminalViewClient?.reportIssueFromTranscript()
                true
            }

            else -> super.onContextItemSelected(item)
        }
    }

    override fun onContextMenuClosed(menu: Menu) {
        super.onContextMenuClosed(menu)
        // onContextMenuClosed() is triggered twice if the back button is pressed to dismiss.
        mTerminalView?.onContextMenuClosed(menu)
    }

    private fun showKillSessionDialog(session: TerminalSession?) {
        if (session == null) return
        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.title_confirm_kill_process)
            .setPositiveButton(android.R.string.yes) { dialog, _ ->
                dialog.dismiss()
                session.finishIfRunning()
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun onResetTerminalSession(session: TerminalSession?) {
        if (session != null) {
            session.reset()
            showToast(resources.getString(R.string.msg_terminal_reset), true)
            mTermuxTerminalSessionActivityClient?.onResetTerminalSession()
        }
    }

    private fun showStylingDialog() {
        val stylingIntent = Intent()
        stylingIntent.setClassName(
            TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME
        )
        try {
            startActivity(stylingIntent)
        } catch (e: ActivityNotFoundException) {
            showStylingInstallDialog()
        } catch (e: IllegalArgumentException) {
            // startActivity() is not documented to throw IllegalArgumentException, but crash
            // reporting shows that it sometimes does, so catch it here.
            showStylingInstallDialog()
        }
    }

    private fun showStylingInstallDialog() {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.error_styling_not_installed))
            .setPositiveButton(R.string.action_styling_install) { _, _ ->
                ActivityUtils.startActivity(
                    this,
                    Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun toggleKeepScreenOn() {
        val terminalView = mTerminalView ?: return
        val preferences = mPreferences ?: return
        if (terminalView.keepScreenOn) {
            terminalView.keepScreenOn = false
            preferences.setKeepScreenOn(false)
        } else {
            terminalView.keepScreenOn = true
            preferences.setKeepScreenOn(true)
        }
    }

    // ---------------------------------------------------------------- storage permission

    /**
     * For processes to access primary external storage (`/sdcard`, `/storage/emulated/0`,
     * `~/storage/shared`), Termux needs the legacy WRITE_EXTERNAL_STORAGE or
     * MANAGE_EXTERNAL_STORAGE permission when targeting targetSdkVersion 30 and running on
     * android 11 or higher.
     */
    fun requestStoragePermission(isPermissionCallback: Boolean) {
        object : Thread() {
            override fun run() {
                // Do not ask for permission again when this is the callback.
                val requestCode =
                    if (isPermissionCallback) -1 else PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION

                // If the permission is granted, then also set up the storage symlinks.
                if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                        this@TermuxActivity, requestCode, true, !isPermissionCallback
                    )
                ) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(
                            this@TermuxActivity, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request)
                        )
                    TermuxInstaller.setupStorageSymlinks(this@TermuxActivity)
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(
                            this@TermuxActivity, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request)
                        )
                }
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Logger.logVerbose(
            LOG_TAG,
            "onActivityResult: requestCode: $requestCode, resultCode: $resultCode, data: " +
                IntentUtils.getIntentString(data)
        )
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Logger.logVerbose(
            LOG_TAG,
            "onRequestPermissionsResult: requestCode: $requestCode, permissions: " +
                permissions.contentToString() + ", grantResults: " + grantResults.contentToString()
        )
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true)
        }
    }

    // ---------------------------------------------------------------- accessors used by clients

    fun getNavBarHeight(): Int = mNavBarHeight

    fun getTermuxActivityRootView(): TermuxActivityRootView? = mTermuxActivityRootView

    fun getTermuxActivityBottomSpaceView(): View? = mTermuxActivityBottomSpaceView

    fun getExtraKeysView(): ExtraKeysView? = mExtraKeysView

    fun getTermuxTerminalExtraKeys(): TermuxTerminalExtraKeys? = mTermuxTerminalExtraKeys

    fun setExtraKeysView(extraKeysView: ExtraKeysView?) {
        mExtraKeysView = extraKeysView
    }

    fun getDrawer(): DrawerLayout? = findViewById(R.id.drawer_layout)

    fun isTerminalViewSelected(): Boolean {
        // The toolbar only shows the extra keys now, so the terminal view is always selected.
        return true
    }

    fun isVisible(): Boolean = mIsVisible

    fun isOnResumeAfterOnCreate(): Boolean = mIsOnResumeAfterOnCreate

    fun isActivityRecreated(): Boolean = mIsActivityRecreated

    fun getTermuxService(): TermuxService? = mTermuxService

    fun getTerminalView(): TerminalView? = mTerminalView

    fun getTermuxTerminalViewClient(): TermuxTerminalViewClient? = mTermuxTerminalViewClient

    fun getTermuxTerminalSessionClient(): TermuxTerminalSessionActivityClient? =
        mTermuxTerminalSessionActivityClient

    fun getCurrentSession(): TerminalSession? = mTerminalView?.currentSession

    fun getPreferences(): TermuxAppSharedPreferences? = mPreferences

    fun getProperties(): TermuxAppSharedProperties? = mProperties

    // ---------------------------------------------------------------- styling broadcasts

    private fun registerTermuxActivityBroadcastReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH)
            addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE)
            addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS)
        }
        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter)
    }

    private fun unregisterTermuxActivityBroadcastReceiver() {
        try {
            unregisterReceiver(mTermuxActivityBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered; ignore.
        }
    }

    private fun fixTermuxActivityBroadcastReceiverIntent(intent: Intent) {
        val extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE)
        if ("storage" == extraReloadStyle) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE)
            intent.action = TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS
        }
    }

    private inner class TermuxActivityBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            if (!mIsVisible) return

            fixTermuxActivityBroadcastReceiverIntent(intent)

            when (intent.action) {
                TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH -> {
                    Logger.logDebug(LOG_TAG, "Received intent to notify app crash")
                    TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG)
                }

                TERMUX_ACTIVITY.ACTION_RELOAD_STYLE -> {
                    Logger.logDebug(LOG_TAG, "Received intent to reload styling")
                    reloadActivityStyling(
                        intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true)
                    )
                }

                TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS -> {
                    Logger.logDebug(LOG_TAG, "Received intent to request storage permissions")
                    requestStoragePermission(false)
                }
            }
        }
    }

    private fun reloadActivityStyling(recreateActivity: Boolean) {
        mProperties?.let { properties ->
            reloadProperties()

            mExtraKeysView?.let { extraKeysView ->
                extraKeysView.setButtonTextAllCaps(properties.shouldExtraKeysTextBeAllCaps())
                mTermuxTerminalExtraKeys?.let { extraKeys ->
                    extraKeysView.reload(extraKeys.extraKeysInfo, mTerminalToolbarDefaultHeight)
                }
            }

            // Update NightMode.APP_NIGHT_MODE.
            TermuxThemeUtils.setAppNightMode(properties.nightMode)
        }

        setMargins()
        setTerminalToolbarHeight()

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this)

        mTermuxTerminalSessionActivityClient?.onReloadActivityStyling()
        mTermuxTerminalViewClient?.onReloadActivityStyling()

        // To change the activity and drawer theme, the activity needs to be recreated. It will
        // destroy the activity, including all stored variables and views, and onCreate() will be
        // called again. Extra keys input text, terminal sessions and transcripts are preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity")
            recreate()
        }
    }
}
