package com.termux.app.terminal;

import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.tabs.TabLayout;
import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.TermuxService;
import com.termux.shared.interact.MessageDialogUtils;
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession;
import com.termux.terminal.TerminalSession;

import java.util.List;

public class TermuxSessionsListViewController implements TabLayout.OnTabSelectedListener {

    final TermuxActivity mActivity;
    final TabLayout mTabLayout;

    private boolean mIsUpdatingTabs;

    public TermuxSessionsListViewController(TermuxActivity activity, TabLayout tabLayout, List<TermuxSession> sessionList) {
        this.mActivity = activity;
        this.mTabLayout = tabLayout;
        this.mTabLayout.addOnTabSelectedListener(this);
        updateSessionList(sessionList);
    }

    public void updateSessionList(@NonNull List<TermuxSession> sessionList) {
        mIsUpdatingTabs = true;

        mTabLayout.removeAllTabs();

        for (int i = 0; i < sessionList.size(); i++) {
            final int position = i;
            final TermuxSession termuxSession = sessionList.get(i);

            TabLayout.Tab tab = mTabLayout.newTab();
            tab.setCustomView(R.layout.terminal_session_tab_view);
            initTabView(tab, termuxSession, position);
            tab.setContentDescription(getSessionTitle(termuxSession));

            tab.view.setOnLongClickListener(v -> {
                TermuxSession session = getTermuxSession(position);
                if (session != null)
                    mActivity.getTermuxTerminalSessionClient().renameSession(session.getTerminalSession());
                return true;
            });

            mTabLayout.addTab(tab);
        }

        selectSession(mActivity.getCurrentSession());

        // The tab selection listeners are disabled while updating, so sync the selected
        // state of the custom views manually to reflect the initial selection.
        syncTabSelectedStates();

        mIsUpdatingTabs = false;

        // If the TabLayout has not completed its first layout yet, tabs added and selected
        // programmatically may not be measured or scrolled into view (known TabLayout
        // behavior). Refresh the layout and scroll to the selected tab (which is the newly
        // added one when a session was just created) once layout has finished.
        mTabLayout.post(() -> {
            mTabLayout.requestLayout();
            int selected = mTabLayout.getSelectedTabPosition();
            if (selected >= 0)
                mTabLayout.setScrollPosition(selected, 0f, false);
        });
    }

    /**
     * Initialize the custom view of a tab: session number badge, session title and a close
     * button to kill the session.
     */
    private void initTabView(TabLayout.Tab tab, TermuxSession termuxSession, int position) {
        View customView = tab.getCustomView();
        if (customView == null) return;

        TextView sessionNumberView = customView.findViewById(R.id.terminal_session_tab_number);
        if (sessionNumberView != null)
            sessionNumberView.setText(String.valueOf(position + 1));

        TextView sessionTitleView = customView.findViewById(R.id.terminal_session_tab_title);
        if (sessionTitleView != null)
            sessionTitleView.setText(getSessionTitle(termuxSession));

        TextView closeButton = customView.findViewById(R.id.terminal_session_tab_close);
        if (closeButton != null)
            closeButton.setOnClickListener(v -> confirmCloseSession(termuxSession));
    }

    private void confirmCloseSession(TermuxSession termuxSession) {
        if (termuxSession == null) return;
        TerminalSession terminalSession = termuxSession.getTerminalSession();
        if (terminalSession == null) return;

        String sessionTitle = getSessionTitle(termuxSession);

        MessageDialogUtils.showMessage(mActivity,
            mActivity.getString(R.string.title_confirm_close_session),
            mActivity.getString(R.string.msg_confirm_close_session, sessionTitle),
            mActivity.getString(R.string.action_close_session),
            (dialog, which) -> closeSession(termuxSession),
            mActivity.getString(android.R.string.cancel),
            null, null);
    }

    private void closeSession(TermuxSession termuxSession) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null || termuxSession == null) return;

        TerminalSession terminalSession = termuxSession.getTerminalSession();
        if (terminalSession == null) return;

        int index = service.getIndexOfSession(terminalSession);
        if (index < 0) return;

        // Kill the session if it is still running.
        if (terminalSession.isRunning())
            terminalSession.finishIfRunning();
        termuxSession.killIfExecuting(mActivity, true);

        // Remove the session from the list and switch to the session that took its place,
        // instead of waiting for the onSessionFinished callback (which only removes sessions
        // that exited with code 0 or 130, not ones killed with SIGKILL).
        service.removeTermuxSession(terminalSession);

        int size = service.getTermuxSessionsSize();
        if (size == 0) {
            mActivity.finishActivityIfNotFinishing();
        } else {
            if (index >= size) index = size - 1;
            TermuxSession neighbourSession = service.getTermuxSession(index);
            if (neighbourSession != null)
                mActivity.getTermuxTerminalSessionClient().setCurrentSession(neighbourSession.getTerminalSession());
        }

        mActivity.termuxSessionListNotifyUpdated();
    }

    public void selectSession(TerminalSession session) {
        if (session == null) return;

        TermuxService service = mActivity.getTermuxService();
        if (service == null) return;

        int index = service.getIndexOfSession(session);
        if (index >= 0 && index < mTabLayout.getTabCount())
            mTabLayout.selectTab(mTabLayout.getTabAt(index));
    }

    private void syncTabSelectedStates() {
        for (int i = 0; i < mTabLayout.getTabCount(); i++) {
            TabLayout.Tab tab = mTabLayout.getTabAt(i);
            if (tab == null) continue;
            View customView = tab.getCustomView();
            if (customView != null)
                customView.setSelected(tab.isSelected());
        }
    }

    @Override
    public void onTabSelected(TabLayout.Tab tab) {
        if (mIsUpdatingTabs) return;

        View customView = tab.getCustomView();
        if (customView != null) customView.setSelected(true);

        TermuxSession session = getTermuxSession(tab.getPosition());
        if (session != null)
            mActivity.getTermuxTerminalSessionClient().setCurrentSession(session.getTerminalSession());
    }

    @Override
    public void onTabUnselected(TabLayout.Tab tab) {
        if (mIsUpdatingTabs) return;

        View customView = tab.getCustomView();
        if (customView != null) customView.setSelected(false);
    }

    @Override
    public void onTabReselected(TabLayout.Tab tab) {
        // Unused.
    }

    private TermuxSession getTermuxSession(int index) {
        TermuxService service = mActivity.getTermuxService();
        if (service == null) return null;
        return service.getTermuxSession(index);
    }

    private String getSessionTitle(TermuxSession termuxSession) {
        TerminalSession session = termuxSession.getTerminalSession();
        if (session == null) return "null session";

        // Only use the user-renamed session name so that the tab title stays stable and
        // does not change to the shell title (like the current directory "~").
        String name = session.mSessionName;
        if (!TextUtils.isEmpty(name)) return name;
        return "session";
    }

}
