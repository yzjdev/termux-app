package com.termux.app.atomcode;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * One model entry from atomcode's {@code [models."<id>"]} config tables.
 *
 * <p>{@link #id} is the table key (what the user selects in atomcode), while {@link #model} is
 * the name sent to the provider and {@link #account} points at a
 * {@code [provider_accounts.*]} entry.</p>
 */
public class AtomCodeModel {

    /** Table key, e.g. {@code trae:deepseek-v4.1-flash}. */
    public String id = "";
    /** Name of the provider account this model is served by. */
    public String account = "";
    /** Model name passed to the provider; usually the same as {@link #id}. */
    public String model = "";
    /** Context window in tokens, or null when the config leaves it unset. */
    public Integer contextWindow;
    public boolean supportsVision;
    /** Reasoning effort levels the model accepts, in config order. */
    public final List<String> reasoningEffortLevels = new ArrayList<>();

    /**
     * The table key this entry had when it was read, so renaming a model can drop the old
     * section instead of leaving a duplicate behind. Null for entries not yet saved.
     */
    public String originalId;

    /** Whether the entry exists in the config file (false for a model being added). */
    public boolean isNew;

    public AtomCodeModel() {
    }

    public AtomCodeModel(@NonNull String id) {
        this.id = id;
    }

    /** Copy of the values a dialog may modify, used to restore them on cancel. */
    public AtomCodeModel copy() {
        AtomCodeModel copy = new AtomCodeModel();
        copy.id = id;
        copy.account = account;
        copy.model = model;
        copy.contextWindow = contextWindow;
        copy.supportsVision = supportsVision;
        copy.reasoningEffortLevels.addAll(reasoningEffortLevels);
        copy.originalId = originalId;
        copy.isNew = isNew;
        return copy;
    }

    /** The model name to show, falling back to the table key when unset. */
    @NonNull
    public String displayModel() {
        return model == null || model.isEmpty() ? id : model;
    }
}
