package com.termux.shared.interact;

import android.content.Context;
import android.content.DialogInterface;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import com.termux.shared.R;

public class MessageDialogUtils {

    /**
     * Show a message in a dialog
     *
     * @param context The {@link Context} to use to start the dialog. An {@link Activity} {@link Context}
     *                must be passed, otherwise exceptions will be thrown.
     * @param titleText The title text of the dialog.
     * @param messageText The message text of the dialog.
     * @param onDismiss The {@link DialogInterface.OnDismissListener} to run when dialog is dismissed.
     */
    public static void showMessage(Context context, String titleText, String messageText, final DialogInterface.OnDismissListener onDismiss) {
        showMessage(context, titleText, messageText, null, null, null, null, onDismiss);
    }

    /**
     * Show a message in a dialog
     *
     * @param context The {@link Context} to use to start the dialog. An {@link Activity} {@link Context}
     *                must be passed, otherwise exceptions will be thrown.
     * @param titleText The title text of the dialog.
     * @param messageText The message text of the dialog.
     * @param positiveText The positive button text of the dialog.
     * @param onPositiveButton The {@link DialogInterface.OnClickListener} to run when positive button
     *                         is pressed.
     * @param negativeText The negative button text of the dialog. If this is {@code null}, then
     *                         negative button will not be shown.
     * @param onNegativeButton The {@link DialogInterface.OnClickListener} to run when negative button
     *                         is pressed.
     * @param onDismiss The {@link DialogInterface.OnDismissListener} to run when dialog is dismissed.
     */
    public static void showMessage(Context context, String titleText, String messageText,
                                   String positiveText,
                                   final DialogInterface.OnClickListener onPositiveButton,
                                   String negativeText,
                                   final DialogInterface.OnClickListener onNegativeButton,
                                   final DialogInterface.OnDismissListener onDismiss) {

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context, com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog);

        builder.setTitle(titleText);
        builder.setMessage(messageText);

        if (positiveText == null)
            positiveText = context.getString(android.R.string.ok);
        builder.setPositiveButton(positiveText, onPositiveButton);

        if (negativeText != null)
            builder.setNegativeButton(negativeText, onNegativeButton);

        if (onDismiss != null)
            builder.setOnDismissListener(onDismiss);

        builder.show();
    }

    public static void exitAppWithErrorMessage(Context context, String titleText, String messageText) {
        showMessage(context, titleText, messageText, dialog -> System.exit(0));
    }

}
