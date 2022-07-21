package com.alexandrepokhodoun.nrf51_app;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;


public class saveDialog extends DialogFragment {


    private String mName = "";
    private EditText mEditName;


    public static saveDialog newInstance() {
        saveDialog fragment = new saveDialog();
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        if (savedInstanceState == null) {


            View v = LayoutInflater.from(getActivity())
                    .inflate(R.layout.dialog_save, null);
            mEditName = (EditText) v.findViewById(R.id.file_edit_text);


            mEditName.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(
                        CharSequence s, int start, int count, int after) {
                    // This space intentionally left blank
                }

                @Override
                public void onTextChanged(
                        CharSequence s, int start, int before, int count) {
                    mName = s.toString();

                }

                @Override
                public void afterTextChanged(Editable s) {
                    // This one too
                }
            });

            return new AlertDialog.Builder(getActivity())
                    .setView(v)
                    .setTitle("Save to Excel file")
                    .setPositiveButton("Save",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {


                                    LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(
                                            new Intent("file_edit")
                                                    .putExtra("name", mName.trim())
                                    );

                                }
                            })
                    .setNegativeButton("Cancel",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                    .create();
        } else {
            setShowsDialog(false);
            dismiss();
            return null;
        }
    }


}

