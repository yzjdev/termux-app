package com.termux.app.atomcode;

import androidx.annotation.NonNull;

/**
 * A provider account entry from atomcode's {@code [provider_accounts.<name>]} config tables.
 *
 * <p>The API key itself is never read into memory: only whether one is configured is kept, so
 * that the key can never leak into a list row, a log or a dialog. A key is only written when the
 * user types a new one (a brand new account, or an explicit key change).</p>
 */
public class AtomCodeAccount {

    /** Table key, e.g. {@code AtomGit} or {@code local}. */
    public String name;
    /** Provider protocol, e.g. {@code openai} or {@code openai-compatible}. */
    public String provider = "";
    public String baseUrl = "";
    /** Whether the config already stores an API key for this account. */
    public boolean hasApiKey;
    /**
     * A key typed in by the user, held only until the account table is written once and then
     * cleared so it is not kept in memory afterwards.
     */
    public String apiKeyToWrite = "";
    /** Whether the user asked for the stored key to be removed from the config. */
    public boolean deleteApiKey;
    /** True when the account does not exist in the config file yet. */
    public boolean isNew;
    /**
     * The table key this account had when it was read, so a rename can drop the old section
     * instead of leaving a duplicate behind. Null for accounts not yet saved.
     */
    public String originalName;

    public AtomCodeAccount(String name) {
        this.name = name;
    }

    /** Whether this account still needs its own table written to the config file. */
    public boolean needsWrite() {
        return isNew;
    }

    /** Whether the account table on disk must be rewritten because of a pending change. */
    public boolean hasPendingWrite() {
        return isNew || !apiKeyToWrite.isEmpty() || deleteApiKey;
    }

    /** Whether the account has a key configured (either already stored or pending a write). */
    public boolean willHaveApiKey() {
        if (deleteApiKey) return false;
        return hasApiKey || !apiKeyToWrite.isEmpty();
    }

    /** Copy of the values a dialog may modify, used to restore them on cancel. */
    @NonNull
    public AtomCodeAccount copy() {
        AtomCodeAccount copy = new AtomCodeAccount(name);
        copy.provider = provider;
        copy.baseUrl = baseUrl;
        copy.hasApiKey = hasApiKey;
        copy.deleteApiKey = deleteApiKey;
        copy.isNew = isNew;
        copy.originalName = originalName;
        return copy;
    }

    /** Restore the values captured by {@link #copy()}. */
    public void restore(@NonNull AtomCodeAccount snapshot) {
        name = snapshot.name;
        provider = snapshot.provider;
        baseUrl = snapshot.baseUrl;
        hasApiKey = snapshot.hasApiKey;
        deleteApiKey = snapshot.deleteApiKey;
        isNew = snapshot.isNew;
        originalName = snapshot.originalName;
    }
}
