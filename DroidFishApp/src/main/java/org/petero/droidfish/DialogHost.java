package org.petero.droidfish;

import android.app.Dialog;

/**
 * Interface for activities that host dialogs created by GenericDialogFragment.
 */
public interface DialogHost {
    Dialog createDialogById(int dialogId);
}
