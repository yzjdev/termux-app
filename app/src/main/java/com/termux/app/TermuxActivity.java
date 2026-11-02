package com.termux.app;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.SharedPreferences;
import android.content.ServiceConnection;
import android.net.Uri;
import android.provider.Settings;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.SystemClock;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.api.file.FileReceiverActivity;
import com.termux.app.terminal.TermuxActivityRootView;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.activities.ReportActivity;
import com.termux.shared.activity.ActivityUtils;
import com.termux.shared.activity.media.AppCompatActivityUtils;
import com.termux.shared.data.IntentUtils;
import com.termux.shared.android.PermissionUtils;
import com.termux.shared.data.DataUtils;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY;
import com.termux.app.activities.FileEditorActivity;
import com.termux.app.activities.HelpActivity;
import com.termux.app.activities.SettingsActivity;
import com.termux.app.file.TextFileDetector;
import com.termux.app.models.QuickCommand;
import com.termux.shared.shell.command.ExecutionCommand;
import com.termux.shared.shell.command.runner.app.AppShell;
import com.termux.shared.termux.shell.TermuxShellManager;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.shared.termux.crash.TermuxCrashUtils;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.app.terminal.TermuxSessionsListViewController;
import com.termux.app.terminal.io.TerminalToolbarViewPager;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.termux.extrakeys.ExtraKeysView;
import com.termux.shared.termux.interact.TextInputDialogUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxUtils;
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties;
import com.termux.shared.termux.theme.TermuxThemeUtils;
import com.termux.shared.theme.NightMode;
import com.termux.shared.view.KeyboardUtils;
import com.termux.shared.view.ViewUtils;
import com.termux.terminal.TerminalSession;
import com.termux.terminal.TerminalSessionClient;
import com.termux.view.TerminalView;
import com.termux.view.TerminalViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.tabs.TabLayout;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A terminal emulator activity.
 * <p/>
 * See
 * <ul>
 * <li>http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android</li>
 * <li>https://code.google.com/p/android/issues/detail?id=6426</li>
 * </ul>
 * about memory leaks.
 */
public final class TermuxActivity extends AppCompatActivity implements ServiceConnection {

    /**
     * The connection to the {@link TermuxService}. Requested in {@link #onCreate(Bundle)} with a call to
     * {@link #bindService(Intent, ServiceConnection, int)}, and obtained and stored in
     * {@link #onServiceConnected(ComponentName, IBinder)}.
     */
    TermuxService mTermuxService;

    /**
     * The {@link TerminalView} shown in  {@link TermuxActivity} that displays the terminal.
     */
    TerminalView mTerminalView;

    /**
     *  The {@link TerminalViewClient} interface implementation to allow for communication between
     *  {@link TerminalView} and {@link TermuxActivity}.
     */
    TermuxTerminalViewClient mTermuxTerminalViewClient;

    /**
     *  The {@link TerminalSessionClient} interface implementation to allow for communication between
     *  {@link TerminalSession} and {@link TermuxActivity}.
     */
    TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    /**
     * Termux app shared preferences manager.
     */
    private TermuxAppSharedPreferences mPreferences;

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private TermuxAppSharedProperties mProperties;

    /**
     * The root view of the {@link TermuxActivity}.
     */
    TermuxActivityRootView mTermuxActivityRootView;

    /**
     * The space at the bottom of {@link @mTermuxActivityRootView} of the {@link TermuxActivity}.
     */
    View mTermuxActivityBottomSpaceView;

    /**
     * The terminal extra keys view.
     */
    ExtraKeysView mExtraKeysView;

    /**
     * The client for the {@link #mExtraKeysView}.
     */
    TermuxTerminalExtraKeys mTermuxTerminalExtraKeys;

    /**
     * The termux sessions list controller.
     */
    TermuxSessionsListViewController mTermuxSessionListViewController;

    /**
     * The {@link TermuxActivity} broadcast receiver for various things like terminal style configuration changes.
     */
    private final BroadcastReceiver mTermuxActivityBroadcastReceiver = new TermuxActivityBroadcastReceiver();

    /**
     * The last toast shown, used cancel current toast before showing new in {@link #showToast(String, boolean)}.
     */
    Toast mLastToast;

    /**
     * Preference keys for the last browsed directory of each storage card.
     */
    private static final String PREF_KEY_DRAWER_FILE_LIST_DIR_FILES = "drawer_file_list_dir_files";
    private static final String PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_FILES = "drawer_file_list_dir_external_files";
    private static final String PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_SHARED = "drawer_file_list_dir_external_shared";

    /**
     * If set, the drawer is not closed in {@link #onStop()}. Used when opening the file editor
     * so that the drawer stays open when coming back from it.
     */
    private boolean mKeepDrawerOpenOnStop;

    /**
     * The storage accordion cards in the drawer (internal, external and shared storage), in
     * top-to-bottom order. Each card remembers its own browsed directory and expanded state.
     */
    private final List<DrawerStorageGroup> mDrawerStorageGroups = new ArrayList<>();

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private boolean mIsVisible;

    /**
     * If onResume() was called after onCreate().
     */
    private boolean mIsOnResumeAfterOnCreate = false;

    /**
     * If activity was restarted like due to call to {@link #recreate()} after receiving
     * {@link TERMUX_ACTIVITY#ACTION_RELOAD_STYLE}, system dark night mode was changed or activity
     * was killed by android.
     */
    private boolean mIsActivityRecreated = false;

    /**
     * The {@link TermuxActivity} is in an invalid state and must not be run.
     */
    private boolean mIsInvalidState;

    private int mNavBarHeight;

    private float mTerminalToolbarDefaultHeight;

    /** The system overlay window root view for the floating terminal panel, or null when closed. */
    private View mFloatingTerminalPanel;

    /** The system overlay window root view for the quick commands list, or null when closed. */
    private View mQuickCommandsOverlay;


    private static final int CONTEXT_MENU_SELECT_URL_ID = 0;
    private static final int CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1;
    private static final int CONTEXT_MENU_SHARE_SELECTED_TEXT = 10;
    private static final int CONTEXT_MENU_AUTOFILL_USERNAME = 11;
    private static final int CONTEXT_MENU_AUTOFILL_PASSWORD = 2;
    private static final int CONTEXT_MENU_RESET_TERMINAL_ID = 3;
    private static final int CONTEXT_MENU_KILL_PROCESS_ID = 4;
    private static final int CONTEXT_MENU_STYLING_ID = 5;
    private static final int CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6;
    private static final int CONTEXT_MENU_HELP_ID = 7;
    private static final int CONTEXT_MENU_SETTINGS_ID = 8;
    private static final int CONTEXT_MENU_REPORT_ID = 9;

    private static final String ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input";
    private static final String ARG_ACTIVITY_RECREATED = "activity_recreated";

    private static final String LOG_TAG = "TermuxActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Logger.logDebug(LOG_TAG, "onCreate");
        mIsOnResumeAfterOnCreate = true;

        if (savedInstanceState != null)
            mIsActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false);

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false);

        // Load Termux app SharedProperties from disk
        mProperties = TermuxAppSharedProperties.getProperties();
        reloadProperties();

        setActivityTheme();

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_termux);

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        mPreferences = TermuxAppSharedPreferences.build(this, true);
        if (mPreferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            mIsInvalidState = true;
            return;
        }

        setMargins();

        mTermuxActivityRootView = findViewById(R.id.activity_termux_root_view);
        mTermuxActivityRootView.setActivity(this);
        mTermuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view);
        mTermuxActivityRootView.setOnApplyWindowInsetsListener(new TermuxActivityRootView.WindowInsetsListener());

        View content = findViewById(android.R.id.content);
        content.setOnApplyWindowInsetsListener((v, insets) -> {
            mNavBarHeight = insets.getSystemWindowInsetBottom();
            return insets;
        });

        if (mProperties.isUsingFullScreen()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        setTermuxTerminalViewAndClients();

        setTerminalToolbarView(savedInstanceState);

        setSettingsButtonView();

        setNewSessionButtonView();

        setDrawerFileListView();

        registerForContextMenu(mTerminalView);

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        try {
            // Start the {@link TermuxService} and make it run regardless of who is bound to it
            Intent serviceIntent = new Intent(this, TermuxService.class);
            startService(serviceIntent);

            // Attempt to bind to the service, this will call the {@link #onServiceConnected(ComponentName, IBinder)}
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw new RuntimeException("bindService() failed");
        } catch (Exception e) {
            Logger.logStackTraceWithMessage(LOG_TAG,"TermuxActivity failed to start TermuxService", e);
            Logger.showToast(this,
                getString(e.getMessage() != null && e.getMessage().contains("app is in background") ?
                    R.string.error_termux_service_start_failed_bg : R.string.error_termux_service_start_failed_general),
                true);
            mIsInvalidState = true;
            return;
        }

        // Send the {@link TermuxConstants#BROADCAST_TERMUX_OPENED} broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this);
    }

    @Override
    public void onStart() {
        super.onStart();

        Logger.logDebug(LOG_TAG, "onStart");

        if (mIsInvalidState) return;

        mIsVisible = true;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStart();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStart();

        if (mPreferences.isTerminalMarginAdjustmentEnabled())
            addTermuxActivityRootViewGlobalLayoutListener();

        registerTermuxActivityBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        Logger.logVerbose(LOG_TAG, "onResume");

        if (mIsInvalidState) return;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onResume();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onResume();

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG);

        mIsOnResumeAfterOnCreate = false;
    }

    @Override
    protected void onStop() {
        super.onStop();

        Logger.logDebug(LOG_TAG, "onStop");

        if (mIsInvalidState) return;

        mIsVisible = false;

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onStop();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onStop();

        removeTermuxActivityRootViewGlobalLayoutListener();

        unregisterTermuxActivityBroadcastReceiver();
        if (!mKeepDrawerOpenOnStop) {
            getDrawer().closeDrawers();
        }
        mKeepDrawerOpenOnStop = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Logger.logDebug(LOG_TAG, "onDestroy");

        if (mIsInvalidState) return;

        // Remove the floating terminal overlay window if it is still attached.
        hideFloatingTerminalPanel();
        hideQuickCommandsOverlay();

        if (mTermuxService != null) {
            // Do not leave service and session clients with references to activity.
            mTermuxService.unsetTermuxTerminalSessionClient();
            mTermuxService = null;
        }

        try {
            unbindService(this);
        } catch (Exception e) {
            // ignore.
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState");

        super.onSaveInstanceState(savedInstanceState);
        saveTerminalToolbarTextInput(savedInstanceState);
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true);
    }





    /**
     * Part of the {@link ServiceConnection} interface. The service is bound with
     * {@link #bindService(Intent, ServiceConnection, int)} in {@link #onCreate(Bundle)} which will cause a call to this
     * callback method.
     */
    @Override
    public void onServiceConnected(ComponentName componentName, IBinder service) {
        Logger.logDebug(LOG_TAG, "onServiceConnected");

        mTermuxService = ((TermuxService.LocalBinder) service).service;

        setTermuxSessionsListView();

        final Intent intent = getIntent();
        setIntent(null);

        if (mTermuxService.isTermuxSessionsEmpty()) {
            if (mIsVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(TermuxActivity.this, () -> {
                    if (mTermuxService == null) return; // Activity might have been destroyed.
                    try {
                        boolean launchFailsafe = false;
                        if (intent != null && intent.getExtras() != null) {
                            launchFailsafe = intent.getExtras().getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                        }
                        mTermuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null);
                    } catch (WindowManager.BadTokenException e) {
                        // Activity finished - ignore.
                    }
                });
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing();
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!mIsActivityRecreated && intent != null && Intent.ACTION_RUN.equals(intent.getAction())) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                boolean isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false);
                mTermuxTerminalSessionActivityClient.addNewSession(isFailSafe, null);
            } else {
                mTermuxTerminalSessionActivityClient.setCurrentSession(mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast());
            }
        }

        // Update the {@link TerminalSession} and {@link TerminalEmulator} clients.
        mTermuxService.setTermuxTerminalSessionClient(mTermuxTerminalSessionActivityClient);

        // A session may have already been created synchronously above (by the
        // setupBootstrapIfNeeded() callback or the ACTION_RUN shortcut branch) before the
        // client was set, so the notify inside createTermuxSession() was skipped. Refresh
        // the sessions list view now to make sure the tabs are up to date.
        termuxSessionListNotifyUpdated();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected");

        // Respect being stopped from the {@link TermuxService} notification action.
        finishActivityIfNotFinishing();
    }






    private void reloadProperties() {
        mProperties.loadTermuxPropertiesFromDisk();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadProperties();
    }



    private void setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().getName(), true);
    }

    private void setMargins() {
        RelativeLayout relativeLayout = findViewById(R.id.activity_termux_root_relative_layout);
        int marginVertical = mProperties.getTerminalMarginVertical();
        // 水平 margin 固定为 0：否则 RelativeLayout（DrawerLayout 父容器）会被整体右移，
        // 导致侧栏左缘无法紧贴屏幕边缘，露出黑色窗口背景形成黑线。
        ViewUtils.setLayoutMarginsInDp(relativeLayout, 0, marginVertical, 0, marginVertical);
    }



    public void addTermuxActivityRootViewGlobalLayoutListener() {
        getTermuxActivityRootView().getViewTreeObserver().addOnGlobalLayoutListener(getTermuxActivityRootView());
    }

    public void removeTermuxActivityRootViewGlobalLayoutListener() {
        if (getTermuxActivityRootView() != null)
            getTermuxActivityRootView().getViewTreeObserver().removeOnGlobalLayoutListener(getTermuxActivityRootView());
    }



    private void setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        mTermuxTerminalSessionActivityClient = new TermuxTerminalSessionActivityClient(this);
        mTermuxTerminalViewClient = new TermuxTerminalViewClient(this, mTermuxTerminalSessionActivityClient);

        // Set termux terminal view
        mTerminalView = findViewById(R.id.terminal_view);
        mTerminalView.setTerminalViewClient(mTermuxTerminalViewClient);

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onCreate();

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onCreate();
    }

    private void setTermuxSessionsListView() {
        TabLayout termuxSessionsTabLayout = findViewById(R.id.terminal_sessions_tab_layout);
        mTermuxSessionListViewController = new TermuxSessionsListViewController(this, termuxSessionsTabLayout,
            getTermuxSessionsForTabList());
    }

    /** The sessions shown in the main interface's tab list; overlay-only sessions are excluded. */
    private List<TermuxSession> getTermuxSessionsForTabList() {
        List<TermuxSession> sessions = new ArrayList<>();
        if (mTermuxService == null) return sessions;
        for (TermuxSession termuxSession : mTermuxService.getTermuxSessions()) {
            if (!termuxSession.getExecutionCommand().isFloatingPanelSession)
                sessions.add(termuxSession);
        }
        return sessions;
    }



    private void setTerminalToolbarView(Bundle savedInstanceState) {
        mTermuxTerminalExtraKeys = new TermuxTerminalExtraKeys(this, mTerminalView,
            mTermuxTerminalViewClient, mTermuxTerminalSessionActivityClient);

        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (mPreferences.shouldShowTerminalToolbar()) terminalToolbarViewPager.setVisibility(View.VISIBLE);

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        mTerminalToolbarDefaultHeight = layoutParams.height;

        setTerminalToolbarHeight();

        String savedTextInput = null;
        if (savedInstanceState != null)
            savedTextInput = savedInstanceState.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT);

        terminalToolbarViewPager.setAdapter(new TerminalToolbarViewPager.PageAdapter(this, savedTextInput));
        terminalToolbarViewPager.addOnPageChangeListener(new TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager));
    }

    private void setTerminalToolbarHeight() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        ViewGroup.LayoutParams layoutParams = terminalToolbarViewPager.getLayoutParams();
        layoutParams.height = Math.round(mTerminalToolbarDefaultHeight *
            (mTermuxTerminalExtraKeys.getExtraKeysInfo() == null ? 0 : mTermuxTerminalExtraKeys.getExtraKeysInfo().getMatrix().length) *
            mProperties.getTerminalToolbarHeightScaleFactor());
        terminalToolbarViewPager.setLayoutParams(layoutParams);
    }

    public void toggleTerminalToolbar() {
        final ViewPager terminalToolbarViewPager = getTerminalToolbarViewPager();
        if (terminalToolbarViewPager == null) return;

        final boolean showNow = mPreferences.toogleShowTerminalToolbar();
        Logger.showToast(this, (showNow ? getString(R.string.msg_enabling_terminal_toolbar) : getString(R.string.msg_disabling_terminal_toolbar)), true);
        terminalToolbarViewPager.setVisibility(showNow ? View.VISIBLE : View.GONE);
        if (showNow && isTerminalToolbarTextInputViewSelected()) {
            // Focus the text input view if just revealed.
            findViewById(R.id.terminal_toolbar_text_input).requestFocus();
        }
    }

    private void saveTerminalToolbarTextInput(Bundle savedInstanceState) {
        if (savedInstanceState == null) return;

        final EditText textInputView = findViewById(R.id.terminal_toolbar_text_input);
        if (textInputView != null) {
            String textInput = textInputView.getText().toString();
            if (!textInput.isEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput);
        }
    }



    private void setSettingsButtonView() {
        MaterialButton settingsButton = findViewById(R.id.settings_button);
        settingsButton.setOnClickListener(v -> {
            ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
        });
    }

    private void setNewSessionButtonView() {
        setNewSessionButtonClickListeners(findViewById(R.id.new_tab_session_button));
    }

    /** Show the quick commands list in an in-app floating panel (opened from the terminal toolbar). */
    public void showQuickCommandsDialog() {
        loadQuickCommands();
        if (mQuickCommandsOverlay != null) hideQuickCommandsOverlay();

        mQuickCommandsOverlay = getLayoutInflater().inflate(R.layout.dialog_floating_quick_commands, null, false);
        mQuickCommandsOverlay.findViewById(R.id.floating_quick_commands_close_button)
            .setOnClickListener(v -> hideQuickCommandsOverlay());
        // The reset button should not close the overlay: the list updates in place.
        View resetButton = mQuickCommandsOverlay.findViewById(R.id.floating_quick_commands_reset_button);
        if (resetButton != null) resetButton.setOnClickListener(v -> resetQuickCommands());

        RecyclerView list = mQuickCommandsOverlay.findViewById(R.id.floating_quick_commands_view);
        if (list == null) return;
        list.setLayoutManager(new LinearLayoutManager(this));
        mQuickCommandListAdapter = new QuickCommandAdapter(this::hideQuickCommandsOverlay);
        list.setAdapter(mQuickCommandListAdapter);
        attachQuickCommandDragReorder(list, mQuickCommandListAdapter);

        // Cap the list height so a long command list cannot stretch the overlay over most of the
        // screen; shorter lists still shrink-wrap their content.
        final int maxListHeight = (int) (getResources().getDisplayMetrics().heightPixels * 0.45f);
        list.post(() -> {
            if (list.getHeight() <= maxListHeight) return;
            ViewGroup.LayoutParams params = list.getLayoutParams();
            params.height = maxListHeight;
            list.setLayoutParams(params);
        });

        MaterialButton addButton = mQuickCommandsOverlay.findViewById(R.id.floating_quick_command_add_button);
        if (addButton != null) addButton.setOnClickListener(v -> showQuickCommandEditDialog(-1));

        // Attach the overlay inside the activity content so it only shows within the app,
        // centered like the former dialog, with a shrink-wrapped width.
        ViewGroup content = findViewById(android.R.id.content);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        content.addView(mQuickCommandsOverlay, params);
        setupQuickCommandsOverlayDrag();
    }

    /** Make the quick commands panel draggable by its title bar, within the activity content. */
    private void setupQuickCommandsOverlayDrag() {
        if (mQuickCommandsOverlay == null) return;
        View titleBar = mQuickCommandsOverlay.findViewById(R.id.floating_quick_commands_titlebar);
        if (titleBar == null) return;
        titleBar.setOnTouchListener(new View.OnTouchListener() {
            private float mDownRawX, mDownRawY;
            private float mStartTranslationX, mStartTranslationY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        mDownRawX = event.getRawX();
                        mDownRawY = event.getRawY();
                        mStartTranslationX = mQuickCommandsOverlay.getTranslationX();
                        mStartTranslationY = mQuickCommandsOverlay.getTranslationY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (mQuickCommandsOverlay != null) {
                            mQuickCommandsOverlay.setTranslationX(
                                mStartTranslationX + event.getRawX() - mDownRawX);
                            mQuickCommandsOverlay.setTranslationY(
                                mStartTranslationY + event.getRawY() - mDownRawY);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    /** Close the quick commands overlay panel, if shown. */
    private void hideQuickCommandsOverlay() {
        View overlay = mQuickCommandsOverlay;
        if (overlay == null) return;
        mQuickCommandsOverlay = null;
        mQuickCommandListAdapter = null;
        if (overlay.isAttachedToWindow()) {
            ViewGroup parent = (ViewGroup) overlay.getParent();
            if (parent != null) parent.removeView(overlay);
        }
    }

    /** Ensure the "display over other apps" permission is granted; otherwise show a toast,
     * send the user to the settings page and return false. */
    private boolean ensureOverlayPermission() {
        if (Settings.canDrawOverlays(this)) return true;
        Toast.makeText(this, R.string.msg_overlay_permission_required, Toast.LENGTH_LONG).show();
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName())));
        } catch (ActivityNotFoundException e) {
            // Some devices only support the generic overlay settings screen.
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        return false;
    }

    private void runInstallCommand(QuickCommand quickCommand) {
        getDrawer().closeDrawers();
        final String command = quickCommand.command;

        if (mTermuxService == null) {
            // Service not bound yet; fall back to typing the command into the current session.
            TerminalSession session = getCurrentSession();
            if (session != null && session.isRunning())
                session.write(command + "\r");
            return;
        }

        // Per-command setting: when the floating terminal is disabled, run the command as a
        // background task instead of showing the floating terminal panel.
        if (!quickCommand.useFloatingTerminal) {
            TerminalSession currentSession = getCurrentSession();
            String workingDirectory = (currentSession != null)
                ? currentSession.getCwd() : null;

            final android.app.ProgressDialog loading = new android.app.ProgressDialog(this);
            loading.setMessage(getString(R.string.msg_quick_command_running, quickCommand.name));
            loading.setCanceledOnTouchOutside(true);
            loading.show();

            ExecutionCommand executionCommand = new ExecutionCommand(
                TermuxShellManager.getNextShellId(),
                TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh",
                new String[]{"-c", command}, null, workingDirectory,
                ExecutionCommand.Runner.APP_SHELL.getName(), false);
            executionCommand.shellName = quickCommand.name;
            // Register the result callback BEFORE starting the task: a fast command can
            // finish and fire onAppShellExited before createTermuxTask() returns.
            if (quickCommand.notifyResult) {
                mTermuxService.registerAppShellResultCallback(executionCommand.id,
                    (shellId, exitCode, stdout, stderr) -> {
                        loading.dismiss();
                        String output = stdout == null ? "" : stdout.trim();
                        if (output.isEmpty() && stderr != null) output = stderr.trim();
                        String message = (exitCode != null && exitCode == 0)
                            ? getString(R.string.msg_quick_command_success, quickCommand.name) +
                                (output.isEmpty() ? "" : "：\n" + output)
                            : getString(R.string.msg_quick_command_failed,
                                quickCommand.name,
                                exitCode != null ? exitCode : -1,
                                output.isEmpty() ? "-" : output);
                        // Show the result as a toast instead of a modal dialog: a dialog would
                        // cover whichever session window is currently displayed and require an
                        // extra keypress to dismiss when multiple sessions are open.
                        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show();
                    });
            } else {
                mTermuxService.registerAppShellResultCallback(executionCommand.id,
                    (shellId, exitCode, stdout, stderr) -> loading.dismiss());
            }

            AppShell task = mTermuxService.createTermuxTask(executionCommand);
            if (task == null) {
                loading.dismiss();
                return;
            }

            return;
        }

        showFloatingTerminalPanel(command);
    }

    /** Show (or reuse) the floating terminal panel and run the command in a dedicated
     * session attached to its TerminalView. The panel is a real system overlay window
     * added via WindowManager, so it requires the SYSTEM_ALERT_WINDOW ("display over
     * other apps") permission; without it the user is sent to the settings page first.
     */
    private void showFloatingTerminalPanel(String command) {
        if (!ensureOverlayPermission()) return;

        // The panel is fixed at the bottom, full width, and always exactly half the phone
        // screen height.
        int panelHeight = getResources().getDisplayMetrics().heightPixels / 2;

        if (mFloatingTerminalPanel == null) {
            mFloatingTerminalPanel = getLayoutInflater().inflate(R.layout.floating_terminal_panel, null, false);
            mFloatingTerminalPanel.findViewById(R.id.floating_terminal_close_button)
                .setOnClickListener(v -> hideFloatingTerminalPanel());
        }

        // Detach any previous command session still bound to the panel view.
        TerminalView terminalView = mFloatingTerminalPanel.findViewById(R.id.floating_terminal_view);

        // TerminalView requires a TerminalViewClient (e.g. onEmulatorSet is invoked right
        // after attachSession), otherwise it crashes with a NullPointerException.
        terminalView.setTerminalViewClient(new TerminalViewClient() {
            @Override public void onSingleTapUp(MotionEvent e) { }
            @Override public boolean shouldBackButtonBeMappedToEscape() { return false; }
            @Override public boolean shouldEnforceCharBasedInput() { return false; }
            @Override public boolean shouldUseCtrlSpaceWorkaround() { return false; }
            @Override public boolean isTerminalViewSelected() { return false; }
            @Override public void copyModeChanged(boolean copyMode) { }
            @Override public boolean onKeyDown(int keyCode, KeyEvent e, TerminalSession session) { return false; }
            @Override public boolean onKeyUp(int keyCode, KeyEvent e) { return false; }
            @Override public boolean onLongPress(MotionEvent event) { return false; }
            @Override public boolean readControlKey() { return false; }
            @Override public boolean readAltKey() { return false; }
            @Override public boolean readShiftKey() { return false; }
            @Override public boolean readFnKey() { return false; }
            @Override public boolean onCodePoint(int codePoint, boolean ctrlDown, TerminalSession session) { return false; }
            @Override public float onScale(float scale) { return scale; }
            @Override public void onEmulatorSet() { }
            @Override public void logError(String tag, String message) { Logger.logError(tag, message); }
            @Override public void logWarn(String tag, String message) { Logger.logWarn(tag, message); }
            @Override public void logInfo(String tag, String message) { Logger.logInfo(tag, message); }
            @Override public void logDebug(String tag, String message) { Logger.logDebug(tag, message); }
            @Override public void logVerbose(String tag, String message) { Logger.logVerbose(tag, message); }
            @Override public void logStackTraceWithMessage(String tag, String message, Exception e) { Logger.logStackTraceWithMessage(tag, message, e); }
            @Override public void logStackTrace(String tag, Exception e) { Logger.logStackTrace(tag, e); }
        });

        // The panel view is newly inflated, so mRenderer is still null; initialize the renderer
        // with the user's font size before attaching a session, otherwise updateSize() would
        // crash reading mRenderer.mFontWidth.
        terminalView.setTextSize(mPreferences.getFontSize());

        // Run the command in the working directory of the current main terminal session,
        // exactly like addNewSession() does.
        TerminalSession currentSession = getCurrentSession();
        String workingDirectory = (currentSession != null)
            ? currentSession.getCwd() : null;

        // Create a login shell session without going through the `login` binary (and its
        // motd): pass the shell binary explicitly with the login flag, so argv[0] gets the
        // "-" prefix and ~/.profile is loaded, but no startup banner is printed.
        ExecutionCommand executionCommand = new ExecutionCommand(
            TermuxShellManager.getNextShellId(),
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh", null, null,
            workingDirectory, ExecutionCommand.Runner.TERMINAL_SESSION.getName(), false);
        executionCommand.shellName = getString(R.string.title_quick_command_session_name);
        executionCommand.isLoginShellCommand = true;
        executionCommand.isFloatingPanelSession = true;
        TermuxSession termuxSession = mTermuxService.createTermuxSession(executionCommand);
        if (termuxSession == null) return;

        TerminalSession session = termuxSession.getTerminalSession();

        // Show the working directory in the panel title while the command runs.
        String dirDisplay = workingDirectory != null ? workingDirectory : "~";
        TextView panelTitle = mFloatingTerminalPanel.findViewById(R.id.floating_terminal_title);
        if (panelTitle != null) panelTitle.setText(dirDisplay);

        // Use a standalone client so the panel session shares nothing with the main
        // sessions (no tabs, no session list, no shared callbacks).
        session.updateTerminalSessionClient(new FloatingPanelSessionClient());
        terminalView.attachSession(session);

        // Attach the panel as a real system overlay window on top of everything else.
        // It stays focusable so the TerminalView can receive keyboard input.
        if (mFloatingTerminalPanel.getParent() == null) {
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, panelHeight, type,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                android.graphics.PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.BOTTOM;
            windowManager.addView(mFloatingTerminalPanel, params);
        }
        // The shell needs a moment to start; writing immediately can be swallowed before the
        // pty reader is up, so retry until the session is running or a timeout elapses.
        final TerminalSession pendingSession = session;
        final String commandWithNewline = command + "; exit\r";
        final Handler handler = new Handler(getMainLooper());
        final Runnable[] retry = new Runnable[1];
        final long deadline = SystemClock.elapsedRealtime() + 3000;
        retry[0] = new Runnable() {
            @Override
            public void run() {
                if (pendingSession.isRunning() && pendingSession.getPid() > 0) {
                    // Shell is up; run the command once.
                    pendingSession.write(commandWithNewline);
                } else if (SystemClock.elapsedRealtime() < deadline) {
                    handler.postDelayed(retry[0], 200);
                }
            }
        };
        handler.postDelayed(retry[0], 200);
    }

    /** Hide the floating terminal panel (remove the system overlay window) and finish
     * its current session if still running. */
    private void hideFloatingTerminalPanel() {
        View panel = mFloatingTerminalPanel;
        if (panel == null) return;
        mFloatingTerminalPanel = null;
        finishFloatingPanelSession(panel);
        if (panel.isAttachedToWindow()) {
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            try {
                windowManager.removeView(panel);
            } catch (Exception e) {
                // The window may already have been removed by the system.
                Logger.logError(LOG_TAG, "Failed to remove floating terminal overlay: " + e.getMessage());
            }
        }
    }

    /** Finish the terminal session currently attached to the panel's view, if any. */
    private void finishFloatingPanelSession(View panel) {
        TerminalView terminalView = panel.findViewById(R.id.floating_terminal_view);
        TerminalSession session = terminalView.getCurrentSession();
        if (session != null && session.isRunning()) session.finishIfRunning();
    }

    /**
     * {@link TerminalSessionClient} for the session shown in the floating terminal panel.
     * Fully standalone: it only refreshes the panel's TerminalView. It deliberately does NOT
     * delegate to the activity client,
     * so the panel session shares nothing with the main sessions (no tabs, no session list).
     */
    private class FloatingPanelSessionClient extends TermuxTerminalSessionClientBase {

        private final View mPanel;

        FloatingPanelSessionClient() {
            mPanel = mFloatingTerminalPanel;
        }

        @Override
        public void onTextChanged(@NonNull TerminalSession changedSession) {
            TerminalView terminalView = mPanel.findViewById(R.id.floating_terminal_view);
            if (terminalView != null && terminalView.getCurrentSession() == changedSession)
                terminalView.onScreenUpdated();
        }

        @Override
        public void onSessionFinished(@NonNull TerminalSession finishedSession) {
            // 任务执行完毕后不关闭窗口：只从中断（移除）会话，悬浮面板继续显示输出。
            runOnUiThread(() -> {
                // Not delegated to the activity client, so remove the finished session
                // from the service's session list here to avoid leaving it behind.
                if (mTermuxService != null)
                    mTermuxService.removeTermuxSession(finishedSession);
            });
        }
    }

    /** Key for the persisted quick commands JSON array in the activity preferences. */
    private static final String KEY_QUICK_COMMANDS = "drawer_quick_commands";

    /** The quick commands shown in the drawer, loaded from preferences or the built-in defaults. */
    private final List<QuickCommand> mQuickCommands = new ArrayList<>();

    /** Built-in default quick commands used to initialize the persisted list on first run. */
    private static final String[][] DEFAULT_COMMANDS = {
        {"更新源", "apt update && apt upgrade -y"},
        {"安装 zsh", "pkg install -y zsh git vim"},
        {"安装 ohmyzsh", "sh -c \"$(curl -fsSL https://install.ohmyz.sh/)\""},
        {"安装 zsh-autosuggestions",
            "git clone https://github.com/zsh-users/zsh-autosuggestions ${ZSH_CUSTOM:-~/.oh-my-zsh/custom}/plugins/zsh-autosuggestions && (grep -q \"zsh-autosuggestions\" ~/.zshrc || sed -i 's/^plugins=(\\([^)]*\\))/plugins=(\\1 zsh-autosuggestions)/' ~/.zshrc)"},
        {"安装 atomcode", "curl -fsSL https://raw.atomgit.com/atomgit_atomcode/atomcode/raw/main/scripts/install.sh | sh"},
        {"请求存储权限", "termux-setup-storage"}
    };

    private static final int[] COMMAND_ICONS = {
        R.drawable.ic_drawer_refresh,
        R.drawable.ic_drawer_terminal,
        R.drawable.ic_drawer_ohmyzsh,
        R.drawable.ic_drawer_extension,
        R.drawable.ic_drawer_code,
        R.drawable.ic_drawer_bolt,
        R.drawable.ic_drawer_folder
    };

    /**
     * Load the quick commands from preferences, falling back to the built-in defaults
     * (which are then persisted) when nothing has been saved yet.
     */
    private void loadQuickCommands() {
        mQuickCommands.clear();
        SharedPreferences preferences = getSharedPreferences("termux_activity", MODE_PRIVATE);
        String json = preferences.getString(KEY_QUICK_COMMANDS, null);
        boolean loaded = false;
        if (json != null) {
            try {
                JSONArray array = new JSONArray(json);
                for (int i = 0; i < array.length(); i++) {
                    mQuickCommands.add(QuickCommand.fromJson(array.getJSONObject(i)));
                }
                loaded = !mQuickCommands.isEmpty();
            } catch (JSONException e) {
                Logger.logError("TermuxActivity", "Failed to parse saved quick commands: " + e.getMessage());
            }
        }
        if (!loaded) {
            for (int i = 0; i < DEFAULT_COMMANDS.length; i++) {
                mQuickCommands.add(new QuickCommand(DEFAULT_COMMANDS[i][0], DEFAULT_COMMANDS[i][1],
                    COMMAND_ICONS[i % COMMAND_ICONS.length]));
            }
            saveQuickCommands();
        }
    }

    /** Persist the current quick commands list as JSON. */
    private void saveQuickCommands() {
        JSONArray array = new JSONArray();
        for (QuickCommand quickCommand : mQuickCommands) {
            array.put(quickCommand.toJson());
        }
        SharedPreferences preferences = getSharedPreferences("termux_activity", MODE_PRIVATE);
        preferences.edit().putString(KEY_QUICK_COMMANDS, array.toString()).apply();
    }

    /** Assign the next icon from the built-in icon pool for a newly created command. */
    private int nextQuickCommandIcon() {
        return COMMAND_ICONS[mQuickCommands.size() % COMMAND_ICONS.length];
    }

    /** Show the long-press menu for a quick command: run, edit or delete it. */
    private void showQuickCommandMenu(int position) {
        if (position < 0 || position >= mQuickCommands.size()) return;
        final QuickCommand quickCommand = mQuickCommands.get(position);
        final String[] actions = {"运行", "编辑", "删除"};
        new AlertDialog.Builder(this).setTitle(quickCommand.name)
            .setItems(actions, (dialog, which) -> {
                if (which == 0) {
                    runInstallCommand(quickCommand);
                } else if (which == 1) {
                    showQuickCommandEditDialog(position);
                } else {
                    showQuickCommandDeleteDialog(position);
                }
            }).show();
    }

    /** Confirm and delete the quick command at the given position. */
    private void showQuickCommandDeleteDialog(int position) {
        if (position < 0 || position >= mQuickCommands.size()) return;
        final QuickCommand quickCommand = mQuickCommands.get(position);
        new AlertDialog.Builder(this).setTitle(R.string.title_confirm_kill_process)
            .setMessage(getString(R.string.msg_confirm_delete_quick_command, quickCommand.name))
            .setPositiveButton(android.R.string.yes, (dialog, id) -> {
                mQuickCommands.remove(position);
                saveQuickCommands();
                refreshQuickCommandList();
            })
            .setNegativeButton(android.R.string.no, null).show();
    }

    /**
     * Show a dialog with name and command inputs. When position is negative a new command is
     * appended to the list, otherwise the command at the position is updated.
     */
    private void showQuickCommandEditDialog(int position) {
        final boolean isNew = position < 0 || position >= mQuickCommands.size();
        final QuickCommand quickCommand = isNew ? null : mQuickCommands.get(position);

        @SuppressLint("InflateParams")
        View view = getLayoutInflater().inflate(R.layout.dialog_quick_command_edit, null);
        final EditText nameInput = view.findViewById(R.id.quick_command_name_input);
        final EditText commandInput = view.findViewById(R.id.quick_command_command_input);
        if (quickCommand != null) {
            nameInput.setText(quickCommand.name);
            commandInput.setText(quickCommand.command);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
            .setTitle(isNew ? R.string.title_add_quick_command : R.string.title_edit_quick_command)
            .setView(view)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null);

        final AlertDialog dialog = builder.create();
        // When opened from the quick commands overlay, restyle the dialog to match the dark panel.
        final boolean inOverlay = mQuickCommandsOverlay != null;
        dialog.setOnShowListener(d -> {
            if (inOverlay) applyOverlayEditDialogStyle(dialog, view);
            Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            okButton.setOnClickListener(v -> {
                String name = nameInput.getText().toString().trim();
                String command = commandInput.getText().toString().trim();
                if (name.isEmpty() || command.isEmpty()) {
                    Toast.makeText(this, R.string.msg_quick_command_required, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (isNew) {
                    mQuickCommands.add(new QuickCommand(name, command, nextQuickCommandIcon()));
                    saveQuickCommands();
                } else {
                    quickCommand.name = name;
                    quickCommand.command = command;
                    saveQuickCommands();
                }
                dialog.dismiss();
                refreshQuickCommandList();
            });
        });
        dialog.show();
    }

    /** Refresh the quick commands list shown in the quick commands dialog, if it is open. */
    private void refreshQuickCommandList() {
        if (mQuickCommandListAdapter != null) mQuickCommandListAdapter.notifyDataSetChanged();
    }

    /** Recolor the add/edit quick command dialog for the dark quick commands overlay. */
    private void applyOverlayEditDialogStyle(@NonNull AlertDialog dialog, @NonNull View view) {
        int titleColor = ContextCompat.getColor(this, R.color.quick_command_overlay_title);
        int secondaryColor = ContextCompat.getColor(this, R.color.quick_command_overlay_secondary);

        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(
                ContextCompat.getDrawable(this, R.drawable.quick_command_overlay_dialog_background));
            TextView titleView = window.findViewById(android.R.id.title);
            if (titleView != null) titleView.setTextColor(titleColor);
        }

        TextView nameLabel = view.findViewById(R.id.quick_command_name_label);
        TextView commandLabel = view.findViewById(R.id.quick_command_command_label);
        if (nameLabel != null) nameLabel.setTextColor(secondaryColor);
        if (commandLabel != null) commandLabel.setTextColor(secondaryColor);

        EditText nameInput = view.findViewById(R.id.quick_command_name_input);
        EditText commandInput = view.findViewById(R.id.quick_command_command_input);
        nameInput.setTextColor(titleColor);
        nameInput.setHintTextColor(secondaryColor);
        commandInput.setTextColor(titleColor);
        commandInput.setHintTextColor(secondaryColor);

        Button okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        Button cancelButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (okButton != null) okButton.setTextColor(titleColor);
        if (cancelButton != null) cancelButton.setTextColor(secondaryColor);
    }

    /** Attach long-press drag reordering to the quick commands list. */
    private void attachQuickCommandDragReorder(RecyclerView list, QuickCommandAdapter adapter) {
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source,
                                  @NonNull RecyclerView.ViewHolder target) {
                adapter.onMove(source.getAdapterPosition(), target.getAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }

            @Override
            public boolean isLongPressDragEnabled() {
                return true;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                // Persist the new order once the drag is released.
                saveQuickCommands();
            }
        });
        itemTouchHelper.attachToRecyclerView(list);
    }

    /** Reset the quick commands to the built-in defaults. */
    private void resetQuickCommands() {
        mQuickCommands.clear();
        for (int i = 0; i < DEFAULT_COMMANDS.length; i++) {
            mQuickCommands.add(new QuickCommand(DEFAULT_COMMANDS[i][0], DEFAULT_COMMANDS[i][1],
                COMMAND_ICONS[i % COMMAND_ICONS.length]));
        }
        saveQuickCommands();
        refreshQuickCommandList();
    }

    /** The adapter for the quick commands list shown in the quick commands dialog (null if closed). */
    @Nullable
    private QuickCommandAdapter mQuickCommandListAdapter;

    /**
     * Adapter for the quick commands list, used both by the drawer and the quick commands
     * dialog. Each item is a row that runs its command when tapped.
     */
    private class QuickCommandAdapter extends RecyclerView.Adapter<QuickCommandAdapter.CommandViewHolder> {

        /** When non-null, the action that closes the list's host window (e.g. the quick
         * commands overlay) after an action that hides it; null in the drawer. */
        @Nullable
        private final Runnable mCloseAction;

        /** Whether the list is shown in the dark quick commands overlay (vs. the light drawer),
         * so items get an overlay-specific color scheme instead of the drawer's. */
        private final boolean mForOverlay;

        QuickCommandAdapter() {
            this(null, false);
        }

        QuickCommandAdapter(@Nullable Runnable closeAction) {
            this(closeAction, true);
        }

        QuickCommandAdapter(@Nullable Runnable closeAction, boolean forOverlay) {
            mCloseAction = closeAction;
            mForOverlay = forOverlay;
        }

        private void onActionDone() {
            if (mCloseAction != null) mCloseAction.run();
        }



        @NonNull
        @Override
        public CommandViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.drawer_quick_command_item, parent, false);
            if (mForOverlay) {
                applyOverlayItemStyle(view);
                // Shrink-wrap the item so the overlay panel width follows its content
                // instead of stretching to the full screen width.
                ViewGroup.LayoutParams params = view.getLayoutParams();
                params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
                view.setLayoutParams(params);
            }
            return new CommandViewHolder(view);
        }

        /** Recolor an item for the dark quick commands overlay: the shared item layout uses the
         * drawer's light-theme colors (near-black text), which are unreadable on the dark panel. */
        private void applyOverlayItemStyle(@NonNull View itemView) {
            Context context = itemView.getContext();
            itemView.setBackgroundResource(R.drawable.quick_command_overlay_item_background);

            int titleColor = ContextCompat.getColor(context, R.color.quick_command_overlay_title);
            int secondaryColor = ContextCompat.getColor(context, R.color.quick_command_overlay_secondary);
            TextView title = itemView.findViewById(R.id.quick_command_text);
            title.setTextColor(titleColor);
            TextView command = itemView.findViewById(R.id.quick_command_command_text);
            command.setTextColor(secondaryColor);

            ColorStateList mainTint = ColorStateList.valueOf(titleColor);
            ((ImageView) itemView.findViewById(R.id.quick_command_icon)).setImageTintList(mainTint);
            ((ImageView) itemView.findViewById(R.id.quick_command_run_button)).setImageTintList(mainTint);

            // The toggles keep a selected-state selector, but switch to a white-on-dark variant.
            ColorStateList toggleTint =
                AppCompatResources.getColorStateList(context, R.color.quick_command_toggle_tint_overlay);
            ((ImageView) itemView.findViewById(R.id.quick_command_toggle_floating_terminal)).setImageTintList(toggleTint);
            TextView floatingLabel = itemView.findViewById(R.id.quick_command_toggle_floating_terminal_label);
            floatingLabel.setTextColor(toggleTint);
            ((ImageView) itemView.findViewById(R.id.quick_command_toggle_notify_result)).setImageTintList(toggleTint);
            TextView notifyLabel = itemView.findViewById(R.id.quick_command_toggle_notify_result_label);
            notifyLabel.setTextColor(toggleTint);
        }

        @Override
        public void onBindViewHolder(@NonNull CommandViewHolder holder, int position) {
            QuickCommand quickCommand = mQuickCommands.get(position);
            holder.iconView.setImageResource(quickCommand.iconRes);
            holder.textView.setText(quickCommand.name);
            holder.commandTextView.setText(quickCommand.command);
            holder.itemView.setOnClickListener(v -> showQuickCommandMenu(position));
            holder.runButtonView.setOnClickListener(v -> {
                onActionDone();
                runInstallCommand(quickCommand);
            });

            // Per-command execution switches live on the card itself so they can be changed without
            // opening the edit dialog. The tint/background selectors key off the selected state.
            bindQuickCommandToggle(holder.floatingTerminalToggleView,
                holder.floatingTerminalToggleLabelView, quickCommand.useFloatingTerminal, true,
                () -> {
                    quickCommand.useFloatingTerminal = !quickCommand.useFloatingTerminal;
                    saveQuickCommands();
                    notifyItemChanged(position);
                });
            // 「结果提示」只对后台执行生效，悬浮终端模式下置灰，避免出现无意义的开关。
            boolean notifyToggleEnabled = !quickCommand.useFloatingTerminal;
            bindQuickCommandToggle(holder.notifyResultToggleView, holder.notifyResultToggleLabelView,
                quickCommand.notifyResult, notifyToggleEnabled,
                () -> {
                    quickCommand.notifyResult = !quickCommand.notifyResult;
                    saveQuickCommands();
                    notifyItemChanged(position);
                });
        }

        /** Apply the on/off state of a card toggle and route taps to the given action. */
        private void bindQuickCommandToggle(ImageView toggleView, TextView labelView, boolean on,
                                            boolean enabled, Runnable onToggle) {
            // The tint selector keys off the selected state, so the icon and its label both turn
            // black (the drawer primary color) when on and grey when off.
            toggleView.setSelected(on);
            labelView.setSelected(on);
            toggleView.setEnabled(enabled);
            labelView.setEnabled(enabled);
            toggleView.setAlpha(enabled ? 1f : 0.35f);
            labelView.setAlpha(enabled ? 1f : 0.35f);
            View.OnClickListener listener = v -> onToggle.run();
            toggleView.setOnClickListener(listener);
            labelView.setOnClickListener(listener);
        }

        @Override
        public int getItemCount() {
            return mQuickCommands.size();
        }

        /** Move an item when the list is reordered by dragging. */
        public void onMove(int fromPosition, int toPosition) {
            Collections.swap(mQuickCommands, fromPosition, toPosition);
            notifyItemMoved(fromPosition, toPosition);
        }

        /** Keep the item clicked state after a long press starts a drag. */
        public boolean onItemLongPress(@NonNull RecyclerView.ViewHolder viewHolder) {
            return true;
        }

        class CommandViewHolder extends RecyclerView.ViewHolder {

            final TextView textView;
            final TextView commandTextView;
            final ImageView iconView;
            final ImageView runButtonView;
            final ImageView floatingTerminalToggleView;
            final TextView floatingTerminalToggleLabelView;
            final ImageView notifyResultToggleView;
            final TextView notifyResultToggleLabelView;

            CommandViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.quick_command_text);
                commandTextView = itemView.findViewById(R.id.quick_command_command_text);
                iconView = itemView.findViewById(R.id.quick_command_icon);
                runButtonView = itemView.findViewById(R.id.quick_command_run_button);
                floatingTerminalToggleView = itemView.findViewById(R.id.quick_command_toggle_floating_terminal);
                floatingTerminalToggleLabelView = itemView.findViewById(R.id.quick_command_toggle_floating_terminal_label);
                notifyResultToggleView = itemView.findViewById(R.id.quick_command_toggle_notify_result);
                notifyResultToggleLabelView = itemView.findViewById(R.id.quick_command_toggle_notify_result_label);
            }
        }
    }

    /**
     * Set up the three vertically stacked storage accordion cards in the left drawer (internal,
     * external and shared storage). Each card independently expands/collapses (they may be open
     * at the same time); inside a card the user enters subdirectories and navigates back up to
     * the storage root. Each card's browsed directory is persisted and restored when the
     * activity is recreated (rotation, process restart, ...).
     */
    private void setDrawerFileListView() {
        // Disable swipe-to-close while the drawer is open so that horizontal scrolling does not
        // conflict with the drawer closing gesture. The drawer can still be closed
        // programmatically (back button, running commands). While closed, the edge-open gesture
        // is disabled as well; the drawer can only be opened programmatically (e.g. the DRAWER
        // extra key).
        getDrawer().addDrawerListener(new DrawerLayout.SimpleDrawerListener() {
            @Override
            public void onDrawerOpened(@NonNull View drawerView) {
                getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_OPEN);
                // Hide the soft keyboard so it does not cover the drawer content.
                KeyboardUtils.hideSoftKeyboard(TermuxActivity.this, mTerminalView);
            }

            @Override
            public void onDrawerClosed(@NonNull View drawerView) {
                getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        });
        // The drawer is initially closed; lock it so it cannot be opened by the edge gesture.
        getDrawer().setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);

        mDrawerStorageGroups.clear();
        mDrawerStorageGroups.add(new DrawerStorageGroup(
            TermuxConstants.TERMUX_FILES_DIR, R.string.msg_external_files_dir_unavailable,
            R.id.drawer_storage_card_files, R.id.drawer_storage_list_view_files,
            R.id.drawer_storage_back_button_files, R.id.drawer_storage_expand_chevron_files,
            R.id.drawer_storage_path_files, PREF_KEY_DRAWER_FILE_LIST_DIR_FILES));
        mDrawerStorageGroups.add(new DrawerStorageGroup(
            getExternalFilesDir(null), R.string.msg_external_files_dir_unavailable,
            R.id.drawer_storage_card_external_files, R.id.drawer_storage_list_view_external_files,
            R.id.drawer_storage_back_button_external_files, R.id.drawer_storage_expand_chevron_external_files,
            R.id.drawer_storage_path_external_files, PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_FILES));
        mDrawerStorageGroups.add(new DrawerStorageGroup(
            Environment.getExternalStorageDirectory(), R.string.msg_external_shared_dir_unavailable,
            R.id.drawer_storage_card_external_shared, R.id.drawer_storage_list_view_external_shared,
            R.id.drawer_storage_back_button_external_shared, R.id.drawer_storage_expand_chevron_external_shared,
            R.id.drawer_storage_path_external_shared, PREF_KEY_DRAWER_FILE_LIST_DIR_EXTERNAL_SHARED));

        for (DrawerStorageGroup group : mDrawerStorageGroups)
            group.init();
    }

    /**
     * One storage accordion card in the drawer: a header row (tap to expand/collapse, a "go up"
     * button to navigate one level up, an expand chevron) and a directory list. Only one
     * directory level is shown at a time; tapping a directory enters it.
     */
    private class DrawerStorageGroup {

        /** The storage root this card is for (null when the root directory is unavailable). */
        final File root;
        /** Toast message shown when the card is expanded but the root directory is unavailable. */
        final int unavailableMessageId;
        final int cardId, listViewId, backButtonId, chevronId, pathViewId;
        /** Preference key for the last browsed directory of this card. */
        final String dirPrefKey;

        /** The directory currently shown in the list; equals {@link #root} at the top level. */
        File currentDir;
        /** Whether the directory list is currently expanded. */
        boolean expanded = false;

        final List<File> items = new ArrayList<>();
        final DrawerStorageAdapter adapter = new DrawerStorageAdapter();

        DrawerStorageGroup(File root, int unavailableMessageId, int cardId, int listViewId,
                           int backButtonId, int chevronId, int pathViewId, String dirPrefKey) {
            this.root = root;
            this.unavailableMessageId = unavailableMessageId;
            this.cardId = cardId;
            this.listViewId = listViewId;
            this.backButtonId = backButtonId;
            this.chevronId = chevronId;
            this.pathViewId = pathViewId;
            this.dirPrefKey = dirPrefKey;
        }

        void init() {
            ViewGroup card = findViewById(cardId);
            View header = card.getChildAt(0);

            // Tapping the header (anywhere except the up button) expands/collapses the list.
            header.setOnClickListener(v -> {
                if (expanded) {
                    expanded = false;
                } else if (root == null || !root.isDirectory()) {
                    Toast.makeText(TermuxActivity.this, unavailableMessageId, Toast.LENGTH_SHORT).show();
                } else {
                    // Expand at the last browsed directory (or the root on first use).
                    currentDir = root;
                    File lastDir = getLastDir();
                    if (lastDir != null) currentDir = lastDir;
                    expanded = true;
                    refresh();
                }
                updateVisibility();
                updatePathView();
                updateChevron();
            });

            // The up button navigates one directory up, never past the storage root.
            findViewById(backButtonId).setOnClickListener(v -> {
                if (!isAtRoot()) {
                    currentDir = currentDir.getParentFile();
                    refresh();
                    updateVisibility();
                    updatePathView();
                }
            });

            RecyclerView listView = findViewById(listViewId);
            listView.setLayoutManager(new LinearLayoutManager(TermuxActivity.this));
            adapter.group = this;
            listView.setAdapter(adapter);
            updateVisibility();
            updatePathView();
            updateChevron();
        }

        /** Whether the list currently shows the storage root directory. */
        boolean isAtRoot() {
            return root != null && currentDir != null && currentDir.equals(root);
        }

        /** The last browsed directory, or null if nothing was saved or it no longer exists. */
        File getLastDir() {
            if (root == null) return null;
            String savedPath = getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, MODE_PRIVATE)
                .getString(dirPrefKey, null);
            if (savedPath != null) {
                File savedDir = new File(savedPath);
                if (savedDir.isDirectory()) return savedDir;
            }
            return null;
        }

        /** Persist the directory currently shown so it is restored on recreation. */
        void saveDir() {
            if (root == null || currentDir == null) return;
            getSharedPreferences(TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION, MODE_PRIVATE)
                .edit()
                .putString(dirPrefKey, currentDir.getAbsolutePath())
                .apply();
        }

        /** Enter {@code dir}: show its contents in the (expanded) list. */
        void enterDirectory(File dir) {
            if (dir == null || !dir.isDirectory()) return;
            expanded = true;
            currentDir = dir;
            refresh();
            updateVisibility();
            updatePathView();
            updateChevron();
        }

        /**
         * Reload the list for {@link #currentDir}, showing directories first and then files,
         * both sorted by name.
         */
        void refresh() {
            items.clear();
            File[] files = currentDir.listFiles();
            if (files != null) {
                List<File> dirs = new ArrayList<>();
                List<File> regularFiles = new ArrayList<>();
                for (File file : files) {
                    if (file.isDirectory()) dirs.add(file);
                    else regularFiles.add(file);
                }
                dirs.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                regularFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                items.addAll(dirs);
                items.addAll(regularFiles);
            }

            adapter.notifyDataSetChanged();
            updateVisibility();
            updatePathView();
            saveDir();
        }

        /**
         * Show the list when expanded and non-empty (an empty directory shows no list and thus
         * no margin), and show the up button only inside a subdirectory.
         */
        void updateVisibility() {
            findViewById(listViewId).setVisibility(expanded && !items.isEmpty() ? View.VISIBLE : View.GONE);
            int upVisibility = (expanded && !isAtRoot()) ? View.VISIBLE : View.GONE;
            findViewById(backButtonId).setVisibility(upVisibility);
        }

        /** Rotate the header chevron to reflect the expanded state. */
        void updateChevron() {
            ImageView chevron = findViewById(chevronId);
            chevron.setRotation(expanded ? 180f : 0f);
        }

        /**
         * Always show the current directory as a path relative to the storage root under the
         * header (the root itself when collapsed); long-pressing it copies the absolute path
         * to the clipboard.
         */
        void updatePathView() {
            TextView pathView = findViewById(pathViewId);
            File dir = currentDir != null ? currentDir : root;
            String path = dir == null ? "" : dir.getAbsolutePath();
            if (root != null && dir != null && !dir.equals(root) && path.startsWith(root.getAbsolutePath()))
                path = path.substring(root.getAbsolutePath().length());
            pathView.setText(path);

            pathView.setOnLongClickListener(v -> {
                if (dir == null) return true;
                ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(null, dir.getAbsolutePath()));
                Toast.makeText(TermuxActivity.this, dir.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    /**
     * Adapter for one drawer storage card's directory list. Tapping a directory enters it;
     * tapping a text file opens the editor; anything else shows its path.
     */
    private class DrawerStorageAdapter extends RecyclerView.Adapter<DrawerStorageAdapter.FileViewHolder> {

        /** The storage group this adapter lists; attached in {@link DrawerStorageGroup#init()}. */
        DrawerStorageGroup group;

        @NonNull
        @Override
        public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.drawer_file_list_item, parent, false);
            return new FileViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
            File file = group.items.get(position);
            holder.textView.setText(file.getName());
            holder.iconView.setImageResource(file.isDirectory() ? R.drawable.ic_drawer_folder : R.drawable.ic_drawer_file);
        }

        @Override
        public int getItemCount() {
            return group == null ? 0 : group.items.size();
        }

        class FileViewHolder extends RecyclerView.ViewHolder {

            final TextView textView;
            final ImageView iconView;

            FileViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(R.id.drawer_file_list_item_text);
                iconView = itemView.findViewById(R.id.drawer_file_list_item_icon);
                itemView.setOnClickListener(v -> {
                    int position = getBindingAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return;
                    File file = group.items.get(position);
                    if (file.isDirectory()) {
                        group.enterDirectory(file);
                    } else if (TextFileDetector.isTextFile(file)) {
                        // Text file: open it in the sora-editor based text editor. Keep the
                        // drawer open so that it is still open when coming back from the editor.
                        mKeepDrawerOpenOnStop = true;
                        Intent intent = new Intent(TermuxActivity.this, FileEditorActivity.class);
                        intent.putExtra(FileEditorActivity.EXTRA_FILE_PATH, file.getAbsolutePath());
                        startActivity(intent);
                    } else {
                        Toast.makeText(TermuxActivity.this, file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }
    }

    private void setNewSessionButtonClickListeners(View newSessionButton) {
        newSessionButton.setOnClickListener(v -> mTermuxTerminalSessionActivityClient.addNewSession(false, null));
        newSessionButton.setOnLongClickListener(v -> {
            TextInputDialogUtils.textInput(TermuxActivity.this, R.string.title_create_named_session, null,
                R.string.action_create_named_session_confirm, text -> mTermuxTerminalSessionActivityClient.addNewSession(false, text),
                R.string.action_new_session_failsafe, text -> mTermuxTerminalSessionActivityClient.addNewSession(true, text),
                -1, null, null);
            return true;
        });
    }







    @SuppressLint("RtlHardcoded")
    @Override
    public void onBackPressed() {
        if (getDrawer().isDrawerOpen(Gravity.LEFT)) {
            getDrawer().closeDrawers();
        } else {
            finishActivityIfNotFinishing();
        }
    }

    public void finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!TermuxActivity.this.isFinishing()) {
            finish();
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    public void showToast(String text, boolean longDuration) {
        if (text == null || text.isEmpty()) return;
        if (mLastToast != null) mLastToast.cancel();
        mLastToast = Toast.makeText(TermuxActivity.this, text, longDuration ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT);
        mLastToast.setGravity(Gravity.TOP, 0, 0);
        mLastToast.show();
    }



    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        TerminalSession currentSession = getCurrentSession();
        if (currentSession == null) return;

        boolean autoFillEnabled = mTerminalView.isAutoFillEnabled();

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url);
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript);
        if (!DataUtils.isNullOrEmpty(mTerminalView.getStoredSelectedText()))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username);
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password);
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, getResources().getString(R.string.action_kill_process, getCurrentSession().getPid())).setEnabled(currentSession.isRunning());
        menu.add(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal);
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on).setCheckable(true).setChecked(mPreferences.shouldKeepScreenOn());
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help);
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings);
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue);
    }

    /** Hook system menu to show context menu instead. */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mTerminalView.showContextMenu();
        return false;
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        TerminalSession session = getCurrentSession();

        switch (item.getItemId()) {
            case CONTEXT_MENU_SELECT_URL_ID:
                mTermuxTerminalViewClient.showUrlSelection();
                return true;
            case CONTEXT_MENU_SHARE_TRANSCRIPT_ID:
                mTermuxTerminalViewClient.shareSessionTranscript();
                return true;
            case CONTEXT_MENU_SHARE_SELECTED_TEXT:
                mTermuxTerminalViewClient.shareSelectedText();
                return true;
            case CONTEXT_MENU_AUTOFILL_USERNAME:
                mTerminalView.requestAutoFillUsername();
                return true;
            case CONTEXT_MENU_AUTOFILL_PASSWORD:
                mTerminalView.requestAutoFillPassword();
                return true;
            case CONTEXT_MENU_RESET_TERMINAL_ID:
                onResetTerminalSession(session);
                return true;
            case CONTEXT_MENU_KILL_PROCESS_ID:
                showKillSessionDialog(session);
                return true;
            case CONTEXT_MENU_STYLING_ID:
                showStylingDialog();
                return true;
            case CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON:
                toggleKeepScreenOn();
                return true;
            case CONTEXT_MENU_HELP_ID:
                ActivityUtils.startActivity(this, new Intent(this, HelpActivity.class));
                return true;
            case CONTEXT_MENU_SETTINGS_ID:
                ActivityUtils.startActivity(this, new Intent(this, SettingsActivity.class));
                return true;
            case CONTEXT_MENU_REPORT_ID:
                mTermuxTerminalViewClient.reportIssueFromTranscript();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        super.onContextMenuClosed(menu);
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        mTerminalView.onContextMenuClosed(menu);
    }

    private void showKillSessionDialog(TerminalSession session) {
        if (session == null) return;

        final AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setIcon(android.R.drawable.ic_dialog_alert);
        b.setMessage(R.string.title_confirm_kill_process);
        b.setPositiveButton(android.R.string.yes, (dialog, id) -> {
            dialog.dismiss();
            session.finishIfRunning();
        });
        b.setNegativeButton(android.R.string.no, null);
        b.show();
    }

    private void onResetTerminalSession(TerminalSession session) {
        if (session != null) {
            session.reset();
            showToast(getResources().getString(R.string.msg_terminal_reset), true);

            if (mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onResetTerminalSession();
        }
    }

    private void showStylingDialog() {
        Intent stylingIntent = new Intent();
        stylingIntent.setClassName(TermuxConstants.TERMUX_STYLING_PACKAGE_NAME, TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME);
        try {
            startActivity(stylingIntent);
        } catch (ActivityNotFoundException | IllegalArgumentException e) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            new AlertDialog.Builder(this).setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install,
                    (dialog, which) -> ActivityUtils.startActivity(this, new Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL))))
                .setNegativeButton(android.R.string.cancel, null).show();
        }
    }
    private void toggleKeepScreenOn() {
        if (mTerminalView.getKeepScreenOn()) {
            mTerminalView.setKeepScreenOn(false);
            mPreferences.setKeepScreenOn(false);
        } else {
            mTerminalView.setKeepScreenOn(true);
            mPreferences.setKeepScreenOn(true);
        }
    }



    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    public void requestStoragePermission(boolean isPermissionCallback) {
        new Thread() {
            @Override
            public void run() {
                // Do not ask for permission again
                int requestCode = isPermissionCallback ? -1 : PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION;

                // If permission is granted, then also setup storage symlinks.
                if(PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    TermuxActivity.this, requestCode, true, !isPermissionCallback)) {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request));

                    TermuxInstaller.setupStorageSymlinks(TermuxActivity.this);
                } else {
                    if (isPermissionCallback)
                        Logger.logInfoAndShowToast(TermuxActivity.this, LOG_TAG,
                            getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request));
                }
            }
        }.start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: " + requestCode + ", resultCode: "  + resultCode + ", data: "  + IntentUtils.getIntentString(data));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: " + requestCode + ", permissions: "  + Arrays.toString(permissions) + ", grantResults: "  + Arrays.toString(grantResults));
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true);
        }
    }



    public int getNavBarHeight() {
        return mNavBarHeight;
    }

    public TermuxActivityRootView getTermuxActivityRootView() {
        return mTermuxActivityRootView;
    }

    public View getTermuxActivityBottomSpaceView() {
        return mTermuxActivityBottomSpaceView;
    }

    public ExtraKeysView getExtraKeysView() {
        return mExtraKeysView;
    }

    public TermuxTerminalExtraKeys getTermuxTerminalExtraKeys() {
        return mTermuxTerminalExtraKeys;
    }

    public void setExtraKeysView(ExtraKeysView extraKeysView) {
        mExtraKeysView = extraKeysView;
    }

    public DrawerLayout getDrawer() {
        return (DrawerLayout) findViewById(R.id.drawer_layout);
    }


    public ViewPager getTerminalToolbarViewPager() {
        return (ViewPager) findViewById(R.id.terminal_toolbar_view_pager);
    }

    public float getTerminalToolbarDefaultHeight() {
        return mTerminalToolbarDefaultHeight;
    }

    public boolean isTerminalViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 0;
    }

    public boolean isTerminalToolbarTextInputViewSelected() {
        return getTerminalToolbarViewPager().getCurrentItem() == 1;
    }


    public void termuxSessionListNotifyUpdated() {
        if (mTermuxSessionListViewController == null || mTermuxService == null) return;
        mTermuxSessionListViewController.updateSessionList(getTermuxSessionsForTabList());
    }

    public boolean isVisible() {
        return mIsVisible;
    }

    public boolean isOnResumeAfterOnCreate() {
        return mIsOnResumeAfterOnCreate;
    }

    public boolean isActivityRecreated() {
        return mIsActivityRecreated;
    }



    public TermuxService getTermuxService() {
        return mTermuxService;
    }

    public TerminalView getTerminalView() {
        return mTerminalView;
    }

    public TermuxTerminalViewClient getTermuxTerminalViewClient() {
        return mTermuxTerminalViewClient;
    }

    public TermuxTerminalSessionActivityClient getTermuxTerminalSessionClient() {
        return mTermuxTerminalSessionActivityClient;
    }

    @Nullable
    public TerminalSession getCurrentSession() {
        if (mTerminalView != null)
            return mTerminalView.getCurrentSession();
        else
            return null;
    }

    public TermuxAppSharedPreferences getPreferences() {
        return mPreferences;
    }

    public TermuxAppSharedProperties getProperties() {
        return mProperties;
    }




    public static void updateTermuxActivityStyling(Context context, boolean recreateActivity) {
        // Make sure that terminal styling is always applied.
        Intent stylingIntent = new Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity);
        context.sendBroadcast(stylingIntent);
    }

    private void registerTermuxActivityBroadcastReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE);
        intentFilter.addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter);
    }

    private void unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver);
    }

    private void fixTermuxActivityBroadcastReceiverIntent(Intent intent) {
        if (intent == null) return;

        String extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
        if ("storage".equals(extraReloadStyle)) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE);
            intent.setAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS);
        }
    }

    class TermuxActivityBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;

            if (mIsVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent);

                switch (intent.getAction()) {
                    case TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH:
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash");
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG);
                        return;
                    case TERMUX_ACTIVITY.ACTION_RELOAD_STYLE:
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling");
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true));
                        return;
                    case TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS:
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions");
                        requestStoragePermission(false);
                        return;
                    default:
                }
            }
        }
    }

    private void reloadActivityStyling(boolean recreateActivity) {
        if (mProperties != null) {
            reloadProperties();

            if (mExtraKeysView != null) {
                mExtraKeysView.setButtonTextAllCaps(mProperties.shouldExtraKeysTextBeAllCaps());
                mExtraKeysView.reload(mTermuxTerminalExtraKeys.getExtraKeysInfo(), mTerminalToolbarDefaultHeight);
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(mProperties.getNightMode());
        }

        setMargins();
        setTerminalToolbarHeight();

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this);

        if (mTermuxTerminalSessionActivityClient != null)
            mTermuxTerminalSessionActivityClient.onReloadActivityStyling();

        if (mTermuxTerminalViewClient != null)
            mTermuxTerminalViewClient.onReloadActivityStyling();

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity");
            TermuxActivity.this.recreate();
        }
    }



    public static void startTermuxActivity(@NonNull final Context context) {
        ActivityUtils.startActivity(context, newInstance(context));
    }

    public static Intent newInstance(@NonNull final Context context) {
        Intent intent = new Intent(context, TermuxActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

}
