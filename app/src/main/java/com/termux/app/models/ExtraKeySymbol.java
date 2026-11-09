package com.termux.app.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A symbol the user added to the terminal extra keys toolbar.
 *
 * <p>The position is 1-based, matching how users think about rows and columns. A row or column
 * of {@code 0} means the symbol is appended automatically to the end of the toolbar instead of
 * being placed in a fixed cell.</p>
 */
public class ExtraKeySymbol {

    private static final String KEY_SYMBOL = "symbol";
    private static final String KEY_ALIAS = "alias";
    private static final String KEY_ROW = "row";
    private static final String KEY_COLUMN = "column";

    /** The text sent to the terminal when the button is tapped. */
    @NonNull
    public final String symbol;

    /** The text shown on the button, or {@code null} to show {@link #symbol} instead. */
    @Nullable
    public final String alias;

    /** 1-based row, or 0 to append automatically. */
    public final int row;

    /** 1-based column, or 0 to append automatically. */
    public final int column;

    public ExtraKeySymbol(@NonNull String symbol, int row, int column) {
        this(symbol, null, row, column);
    }

    public ExtraKeySymbol(@NonNull String symbol, @Nullable String alias, int row, int column) {
        this.symbol = symbol;
        this.alias = (alias == null || alias.isEmpty()) ? null : alias;
        this.row = row;
        this.column = column;
    }

    /** True when the symbol has an explicit grid cell instead of being appended. */
    public boolean hasPosition() {
        return row > 0 && column > 0;
    }

    /** @return the text to show on the button, which is the alias when one is set. */
    @NonNull
    public String getDisplay() {
        return alias != null ? alias : symbol;
    }

    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put(KEY_SYMBOL, symbol);
        if (alias != null) json.put(KEY_ALIAS, alias);
        if (hasPosition()) {
            json.put(KEY_ROW, row);
            json.put(KEY_COLUMN, column);
        }
        return json;
    }

    /** Parse a saved symbol, accepting the legacy plain-string format as an auto-appended one. */
    @Nullable
    public static ExtraKeySymbol fromJson(@Nullable Object value) {
        if (value instanceof String) return new ExtraKeySymbol((String) value, 0, 0);
        if (!(value instanceof JSONObject)) return null;

        JSONObject json = (JSONObject) value;
        if (!json.has(KEY_SYMBOL)) return null;
        String symbol = json.optString(KEY_SYMBOL);
        if (symbol.isEmpty()) return null;
        return new ExtraKeySymbol(symbol, json.optString(KEY_ALIAS, null),
            json.optInt(KEY_ROW, 0), json.optInt(KEY_COLUMN, 0));
    }

}
