package com.termux.app.terminal.io;

import android.annotation.SuppressLint;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import com.termux.R;
import com.termux.app.TermuxActivity;
import com.termux.app.models.ExtraKeySymbol;
import com.termux.app.terminal.TermuxTerminalSessionActivityClient;
import com.termux.app.terminal.TermuxTerminalViewClient;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.extrakeys.ExtraKeyButton;
import com.termux.shared.termux.extrakeys.ExtraKeysConstants;
import com.termux.shared.termux.extrakeys.ExtraKeysInfo;
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;
import com.termux.shared.termux.settings.properties.TermuxSharedProperties;
import com.termux.shared.termux.terminal.io.TerminalExtraKeys;
import com.termux.view.TerminalView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class TermuxTerminalExtraKeys extends TerminalExtraKeys {

    private ExtraKeysInfo mExtraKeysInfo;

    final TermuxActivity mActivity;
    final TermuxTerminalViewClient mTermuxTerminalViewClient;
    final TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;

    private static final String LOG_TAG = "TermuxTerminalExtraKeys";

    /** The extra key value that opens the quick commands dialog from the terminal toolbar. */
    private static final String EXTRA_KEY_QUICK_COMMAND = "QUICK_COMMAND";

    /** The extra key value that opens the dialog for adding a quick symbol to the toolbar. */
    private static final String EXTRA_KEY_ADD_SYMBOL = "ADD_SYMBOL";

    public TermuxTerminalExtraKeys(TermuxActivity activity, @NonNull TerminalView terminalView,
                                   TermuxTerminalViewClient termuxTerminalViewClient,
                                   TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        super(terminalView);

        mActivity = activity;
        mTermuxTerminalViewClient = termuxTerminalViewClient;
        mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;

        setExtraKeys();
    }


    /**
     * Append the toolbar rows to the extra keys matrix JSON: symbols the user placed at a fixed
     * row/column are inserted into the grid first, then a final row holding the quick commands
     * button, an ENTER key, the remaining auto-appended symbols and the add-symbol button is
     * appended.
     *
     * @param extraKeysJson The extra keys JSON string, an array of arrays.
     * @return The extra keys JSON with the toolbar rows added.
     */
    private String appendQuickCommandRow(String extraKeysJson) throws JSONException {
        JSONArray matrix = new JSONArray(extraKeysJson);
        List<ExtraKeySymbol> symbols = mActivity.getExtraKeySymbols();

        for (ExtraKeySymbol symbol : symbols) {
            if (symbol.hasPosition()) placeExtraKey(matrix, symbol);
        }

        // Match the column count of the toolbar so added symbols line up with the keys above,
        // and wrap into additional rows once a row is full.
        int maxColumns = 1;
        for (int i = 0; i < matrix.length(); i++) {
            maxColumns = Math.max(maxColumns, matrix.getJSONArray(i).length());
        }

        JSONArray row = new JSONArray();
        row.put(createExtraKeyButton(EXTRA_KEY_QUICK_COMMAND,
            mActivity.getString(R.string.title_quick_commands), "ic_extra_key_quick_command"));
        row.put(createExtraKeyButton("ENTER", null, "ic_extra_key_enter"));

        for (ExtraKeySymbol symbol : symbols) {
            if (symbol.hasPosition()) continue;
            if (row.length() >= maxColumns) {
                matrix.put(row);
                row = new JSONArray();
            }
            row.put(createExtraKeyButton(symbol.symbol, symbol.symbol, null));
        }

        // Keep the add button at the end of the toolbar, after the user's symbols.
        if (row.length() >= maxColumns) {
            matrix.put(row);
            row = new JSONArray();
        }
        row.put(createExtraKeyButton(EXTRA_KEY_ADD_SYMBOL, null, "ic_add"));

        matrix.put(row);
        return matrix.toString();
    }

    /** Place a symbol at its 1-based row/column, shifting later keys to the right. */
    private void placeExtraKey(JSONArray matrix, ExtraKeySymbol symbol) throws JSONException {
        int rowIndex = symbol.row - 1;
        while (matrix.length() <= rowIndex) matrix.put(new JSONArray());

        JSONArray row = matrix.getJSONArray(rowIndex);
        int columnIndex = symbol.column - 1;
        while (row.length() < columnIndex) row.put(createBlankExtraKeyButton());

        JSONObject button = createExtraKeyButton(symbol.symbol, symbol.symbol, null);
        if (columnIndex < row.length()) {
            row.put(columnIndex, button);
        } else {
            row.put(button);
        }
    }

    /** Create the JSON config for an extra key button. */
    private JSONObject createExtraKeyButton(String key, String display, String icon) throws JSONException {
        JSONObject button = new JSONObject();
        button.put(ExtraKeyButton.KEY_KEY_NAME, key);
        if (display != null) button.put(ExtraKeyButton.KEY_DISPLAY_NAME, display);
        if (icon != null) button.put(ExtraKeyButton.KEY_ICON, icon);
        return button;
    }

    /** An empty cell used when a symbol is placed beyond the current end of a row. */
    private JSONObject createBlankExtraKeyButton() throws JSONException {
        return createExtraKeyButton("", " ", null);
    }

    /**
     * Set the terminal extra keys and style.
     */
    private void setExtraKeys() {
        mExtraKeysInfo = null;

        try {
            // The mMap stores the extra key and style string values while loading properties
            // Check {@link #getExtraKeysInternalPropertyValueFromValue(String)} and
            // {@link #getExtraKeysStyleInternalPropertyValueFromValue(String)}
            String extrakeys = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS, true);
            String extraKeysStyle = (String) mActivity.getProperties().getInternalPropertyValue(TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE, true);

            ExtraKeysConstants.ExtraKeyDisplayMap extraKeyDisplayMap = ExtraKeysInfo.getCharDisplayMapForStyle(extraKeysStyle);
            if (ExtraKeysConstants.EXTRA_KEY_DISPLAY_MAPS.DEFAULT_CHAR_DISPLAY.equals(extraKeyDisplayMap) && !TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE.equals(extraKeysStyle)) {
                Logger.logError(TermuxSharedProperties.LOG_TAG, "The style \"" + extraKeysStyle + "\" for the key \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS_STYLE + "\" is invalid. Using default style instead.");
                extraKeysStyle = TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE;
            }

            mExtraKeysInfo = new ExtraKeysInfo(appendQuickCommandRow(extrakeys), extraKeysStyle, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
        } catch (JSONException e) {
            Logger.showToast(mActivity, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: " + e.toString(), true);
            Logger.logStackTraceWithMessage(LOG_TAG, "Could not load and set the \"" + TermuxPropertyConstants.KEY_EXTRA_KEYS + "\" property from the properties file: ", e);

            try {
                mExtraKeysInfo = new ExtraKeysInfo(appendQuickCommandRow(TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS), TermuxPropertyConstants.DEFAULT_IVALUE_EXTRA_KEYS_STYLE, ExtraKeysConstants.CONTROL_CHARS_ALIASES);
            } catch (JSONException e2) {
                Logger.showToast(mActivity, "Can't create default extra keys",true);
                Logger.logStackTraceWithMessage(LOG_TAG, "Could create default extra keys: ", e);
                mExtraKeysInfo = null;
            }
        }
    }

    public ExtraKeysInfo getExtraKeysInfo() {
        return mExtraKeysInfo;
    }

    /** @return the number of columns in the extra keys grid, or 1 when it is not loaded. */
    public int getColumnCount() {
        if (mExtraKeysInfo == null) return 1;
        int columns = 1;
        for (ExtraKeyButton[] row : mExtraKeysInfo.getMatrix()) {
            columns = Math.max(columns, row.length);
        }
        return columns;
    }

    /** @return the number of non-empty rows in the extra keys grid, or 0 when it is not loaded. */
    public int getRowCount() {
        if (mExtraKeysInfo == null) return 0;
        int rows = 0;
        for (ExtraKeyButton[] row : mExtraKeysInfo.getMatrix()) {
            if (row.length > 0) rows++;
        }
        return rows;
    }

    /** Rebuild the extra keys info, e.g. after the user adds or removes a quick symbol. */
    public void reloadExtraKeysInfo() {
        setExtraKeys();
    }

    @SuppressLint("RtlHardcoded")
    @Override
    public void onTerminalExtraKeyButtonClick(View view, String key, boolean ctrlDown, boolean altDown, boolean shiftDown, boolean fnDown) {
        if (EXTRA_KEY_QUICK_COMMAND.equals(key)) {
            mActivity.showQuickCommandsDialog();
        } else if (EXTRA_KEY_ADD_SYMBOL.equals(key)) {
            mActivity.showAddExtraKeySymbolDialog();
        } else if ("KEYBOARD".equals(key) || "keyb".equals(key)) {
            if(mTermuxTerminalViewClient != null)
                mTermuxTerminalViewClient.onToggleSoftKeyboardRequest();
        } else if ("DRAWER".equals(key)) {
            DrawerLayout drawerLayout = mTermuxTerminalViewClient.getActivity().getDrawer();
            if (drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.closeDrawer(Gravity.LEFT);
            else
                drawerLayout.openDrawer(Gravity.LEFT);
        } else if ("PASTE".equals(key)) {
            if(mTermuxTerminalSessionActivityClient != null)
                mTermuxTerminalSessionActivityClient.onPasteTextFromClipboard(null);
        }  else if ("SCROLL".equals(key)) {
            TerminalView terminalView = mTermuxTerminalViewClient.getActivity().getTerminalView();
            if (terminalView != null && terminalView.mEmulator != null)
                terminalView.mEmulator.toggleAutoScrollDisabled();
        } else {
            super.onTerminalExtraKeyButtonClick(view, key, ctrlDown, altDown, shiftDown, fnDown);
        }
    }

}
