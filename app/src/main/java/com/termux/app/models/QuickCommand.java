package com.termux.app.models;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * A quick command entry shown in the left drawer's quick commands list.
 */
public class QuickCommand {

    public String name;
    public String command;
    /** Resource id of the icon shown for this entry; reused from the built-in icon pool. */
    public int iconRes;

    public QuickCommand(String name, String command, int iconRes) {
        this.name = name;
        this.command = command;
        this.iconRes = iconRes;
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("name", name);
            json.put("command", command);
            json.put("iconRes", iconRes);
        } catch (JSONException e) {
            // Should not happen since values are simple types.
        }
        return json;
    }

    public static QuickCommand fromJson(JSONObject json) {
        return new QuickCommand(
            json.optString("name", ""),
            json.optString("command", ""),
            json.optInt("iconRes", 0));
    }
}
