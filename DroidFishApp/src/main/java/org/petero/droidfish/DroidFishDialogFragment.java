package org.petero.droidfish;

import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

/**
 * Generic DialogFragment that delegates dialog creation to the hosting activity.
 * The activity must implement DialogHost. This avoids needing a separate
 * DialogFragment subclass for each dialog.
 */
public class DroidFishDialogFragment extends DialogFragment {

    private static final String ARG_DIALOG_ID = "dialogId";

    public static DroidFishDialogFragment newInstance(int dialogId) {
        DroidFishDialogFragment f = new DroidFishDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_DIALOG_ID, dialogId);
        f.setArguments(args);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        int dialogId = requireArguments().getInt(ARG_DIALOG_ID);
        DialogHost host = (DialogHost) requireActivity();
        Dialog d = host.createDialogById(dialogId);
        if (d != null)
            return d;
        return super.onCreateDialog(savedInstanceState);
    }
}
