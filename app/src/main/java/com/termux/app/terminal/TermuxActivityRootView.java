package com.termux.app.terminal;

import android.content.Context;
import android.inputmethodservice.InputMethodService;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsCompat;

import com.termux.app.TermuxActivity;
import com.termux.shared.logger.Logger;


/**
 * The {@link TermuxActivity} relies on {@link android.view.WindowManager.LayoutParams#SOFT_INPUT_ADJUST_RESIZE)}
 * set by {@link TermuxTerminalViewClient#setSoftKeyboardState(boolean, boolean)} to automatically
 * resize the view and push the terminal up when soft keyboard is opened. However, this does not
 * always work properly. When `enforce-char-based-input=true` is set in `termux.properties`
 * and {@link com.termux.view.TerminalView#onCreateInputConnection(EditorInfo)} sets the inputType
 * to `InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS`
 * instead of the default `InputType.TYPE_NULL` for termux, some keyboards may still show suggestions.
 * Gboard does too, but only when text is copied and clipboard suggestions **and** number keys row
 * toggles are enabled in its settings. When number keys row toggle is not enabled, Gboard will still
 * show the row but will switch it with suggestions if needed. If its enabled, then number keys row
 * is always shown and suggestions are shown in an additional row on top of it. This additional row is likely
 * part of the candidates view returned by the keyboard app in {@link InputMethodService#onCreateCandidatesView()}.
 *
 * With the above configuration, the additional clipboard suggestions row partially covers the
 * extra keys/terminal. Reopening the keyboard/activity does not fix the issue. This is either a bug
 * in the Android OS where it does not consider the candidate's view height in its calculation to push
 * up the view or because Gboard does not include the candidate's view height in the height reported
 * to android that should be used, hence causing an overlap.
 *
 * Gboard logs the following entry to `logcat` when its opened with or without the suggestions bar showing:
 * I/KeyboardViewUtil: KeyboardViewUtil.calculateMaxKeyboardBodyHeight():62 leave 500 height for app when screen height:2392, header height:176 and isFullscreenMode:false, so the max keyboard body height is:1716
 * where `keyboard_height = screen_height - height_for_app - header_height` (62 is a hardcoded value in Gboard source code and may be a version number)
 * So this may in fact be due to Gboard but https://stackoverflow.com/questions/57567272 suggests
 * otherwise. Another similar report https://stackoverflow.com/questions/66761661.
 * Also check https://github.com/termux/termux-app/issues/1539.
 *
 * This overlap may happen even without `enforce-char-based-input=true` for keyboards with extended layouts
 * like number row, etc.
 *
 * To fix these issues, {@link TermuxActivityRootView} listens to {@link WindowInsetsCompat.Type#ime()}
 * via a {@link View.OnApplyWindowInsetsListener} and shifts the root up by exactly the reported IME
 * (soft keyboard) height. This replaces the old approach of probing a 1dp
 * `activity_termux_bottom_space_view` with {@link View#getWindowVisibleDisplayFrame(android.graphics.Rect)}:
 * under edge-to-edge that frame excludes the navigation bar while the probe sat at the very bottom
 * of the full-screen window, so the raw difference included the navigation bar height even with no
 * keyboard shown. That phantom offset pushed the whole root up and left a black strip above the
 * navigation bar. {@code ime().bottom} is the exact keyboard height and is 0 when the keyboard is
 * hidden, so no manual navigation-bar subtraction or jitter/debounce heuristics are needed.
 */
public class TermuxActivityRootView extends LinearLayout implements ViewTreeObserver.OnGlobalLayoutListener {

    public TermuxActivity mActivity;

    /** Log root view events. */
    private boolean ROOT_VIEW_LOGGING_ENABLED = false;

    private static final String LOG_TAG = "TermuxActivityRootView";

    /**
     * Height of the soft keyboard, read from {@link WindowInsetsCompat.Type#ime()} on every
     * insets dispatch. 0 when the keyboard is hidden.
     */
    private static int mImeHeight;

    public TermuxActivityRootView(Context context) {
        super(context);
    }

    public TermuxActivityRootView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TermuxActivityRootView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setActivity(TermuxActivity activity) {
        mActivity = activity;
    }

    /**
     * Sets whether root view logging is enabled or not.
     *
     * @param value The boolean value that defines the state.
     */
    public void setIsRootViewLoggingEnabled(boolean value) {
        ROOT_VIEW_LOGGING_ENABLED = value;
    }

    @Override
    public void onGlobalLayout() {
        if (mActivity == null || !mActivity.isVisible()) return;

        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) getLayoutParams();

        // Skip in multi/floating window mode: the IME is a detached window here, so its inset
        // does not represent an overlap inside this window and any offset would cause flicker.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mActivity.isInMultiWindowMode()) {
            if (params.bottomMargin != 0) {
                if (ROOT_VIEW_LOGGING_ENABLED)
                    Logger.logVerbose(LOG_TAG, "Resetting bottom margin in multi-window mode");
                params.setMargins(0, 0, 0, 0);
                setLayoutParams(params);
            }
            return;
        }

        // ime().bottom is the exact soft-keyboard height, and 0 when the keyboard is hidden.
        // Unlike the old getWindowVisibleDisplayFrame() probe it never includes the navigation
        // bar, so no subtraction, clamping or jitter/debounce heuristics are needed. Write only
        // on change to avoid a redundant measure/layout pass.
        int keyboardHeight = Math.max(0, mImeHeight);
        if (params.bottomMargin != keyboardHeight) {
            if (ROOT_VIEW_LOGGING_ENABLED)
                Logger.logVerbose(LOG_TAG, "Setting bottom margin to " + keyboardHeight);
            params.setMargins(0, 0, 0, keyboardHeight);
            setLayoutParams(params);
        }
    }

    public static class WindowInsetsListener implements View.OnApplyWindowInsetsListener {
        @Override
        public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
            WindowInsetsCompat compat = WindowInsetsCompat.toWindowInsetsCompat(insets);
            // Exact IME (soft keyboard) height, 0 when hidden. This is the sole input to the
            // keyboard-avoidance margin in onGlobalLayout(), replacing the old probe-based
            // getWindowVisibleDisplayFrame() inference that also counted the navigation bar.
            mImeHeight = compat.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            // Full edge-to-edge: do NOT consume any inset nor apply padding here. The root stays
            // flush to the screen edges so both the terminal and the drawer can draw behind the
            // transparent status/navigation bars. Each subtree applies its own insets via the
            // official WindowInsets APIs (ViewCompat / Compose). ime() insets are independent of
            // this view's padding.
            return insets;
        }
    }

}
