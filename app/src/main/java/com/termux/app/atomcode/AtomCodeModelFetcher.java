package com.termux.app.atomcode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Fetch the model list of an OpenAI-compatible provider account.
 *
 * <p>Requests {@code {base_url}/models} with the account's API key and returns the model ids in
 * the order the provider reports them. The key is passed in per call and never stored, and it
 * never appears in an exception message.</p>
 */
public final class AtomCodeModelFetcher {

    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 20000;
    /** Providers return a small list; refuse anything absurdly large instead of reading it. */
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;

    private AtomCodeModelFetcher() {
    }

    /** Raised when the provider answers, but with an unusable status or body. */
    public static class FetchException extends IOException {
        public FetchException(String message) {
            super(message);
        }
    }

    /**
     * Fetch the model ids of an account.
     *
     * @param account The account to query (its {@code baseUrl} and protocol are used).
     * @param apiKey The account's API key, or null when the account needs none.
     * @return The model ids reported by the provider, in provider order.
     * @throws IOException When the request fails or the response cannot be understood.
     */
    @NonNull
    public static List<String> fetchModelIds(@NonNull AtomCodeAccount account, @Nullable String apiKey)
        throws IOException {
        String baseUrl = account.baseUrl == null ? "" : account.baseUrl.trim();
        if (baseUrl.isEmpty())
            throw new FetchException("账号未配置 base_url");

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(modelsUrl(baseUrl)).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestProperty("Accept", "application/json");
            if (apiKey != null && !apiKey.isEmpty())
                connection.setRequestProperty("Authorization", "Bearer " + apiKey);

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                // Read the error body for a hint, but keep it short: it may echo request details.
                String body = readBody(connection.getErrorStream());
                throw new FetchException("HTTP " + status + errorSuffix(body));
            }
            return parseModelIds(readBody(connection.getInputStream()));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Join the base URL with the models path, tolerating a trailing slash. */
    @NonNull
    private static String modelsUrl(@NonNull String baseUrl) {
        String trimmed = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return trimmed + "/models";
    }

    /**
     * Extract the model ids from an OpenAI-compatible {@code /models} response.
     *
     * <p>Accepts {@code {"data":[{"id":"..."}]}} (the usual shape) as well as a bare array or
     * {@code {"models":[...]}}, since providers differ slightly.</p>
     */
    @NonNull
    static List<String> parseModelIds(@NonNull String body) throws FetchException {
        if (body.trim().isEmpty()) throw new FetchException("返回内容为空");

        Object root;
        try {
            String trimmed = body.trim();
            root = trimmed.startsWith("[") ? new JSONArray(trimmed) : new JSONObject(trimmed);
        } catch (JSONException e) {
            throw new FetchException("返回内容不是合法 JSON");
        }

        JSONArray items = null;
        if (root instanceof JSONArray) {
            items = (JSONArray) root;
        } else {
            JSONObject object = (JSONObject) root;
            items = object.optJSONArray("data");
            if (items == null) items = object.optJSONArray("models");
        }
        if (items == null) throw new FetchException("返回内容中没有模型列表");

        List<String> ids = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) {
            String id = modelIdOf(items.opt(i));
            if (id != null && !ids.contains(id)) ids.add(id);
        }
        if (ids.isEmpty()) throw new FetchException("模型列表为空");
        Collections.sort(ids, String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    /** The id of one list item, which may be a plain string or an object with an {@code id}. */
    @Nullable
    private static String modelIdOf(@Nullable Object item) {
        if (item instanceof String) {
            String id = ((String) item).trim();
            return id.isEmpty() ? null : id;
        }
        if (item instanceof JSONObject) {
            JSONObject object = (JSONObject) item;
            String id = object.optString("id", "").trim();
            if (id.isEmpty()) id = object.optString("name", "").trim();
            return id.isEmpty() ? null : id;
        }
        return null;
    }

    /** Read a response stream as UTF-8, bounded to {@link #MAX_RESPONSE_BYTES}. */
    @NonNull
    private static String readBody(@Nullable InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream in = stream) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = in.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) throw new FetchException("返回内容过大");
                out.write(buffer, 0, count);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** A short, single-line hint from an error body, or an empty string when there is none. */
    @NonNull
    private static String errorSuffix(@NonNull String body) {
        String trimmed = body.trim();
        if (trimmed.isEmpty()) return "";
        String singleLine = trimmed.replaceAll("\\s+", " ");
        if (singleLine.length() > 120) singleLine = singleLine.substring(0, 120) + "…";
        return "：" + singleLine;
    }
}
