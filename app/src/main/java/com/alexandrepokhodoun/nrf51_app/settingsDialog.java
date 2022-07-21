package com.alexandrepokhodoun.nrf51_app;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.NumberPicker;


public class settingsDialog extends DialogFragment {


    private String mName = "";
    private NumberPicker mNumberPicker;
    private int mDefault;
    private int duration;


    public static settingsDialog newInstance() {
        settingsDialog fragment = new settingsDialog();
        return fragment;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        if (savedInstanceState == null) {
            mDefault = getArguments().getInt("duration");
            duration = mDefault;

            View v = LayoutInflater.from(getActivity())
                    .inflate(R.layout.dialog_settings, null);

            mNumberPicker = v.findViewById(R.id.number_picker);
            mNumberPicker.setMaxValue(17);
            mNumberPicker.setValue((mDefault/10)-1);
            mNumberPicker.setWrapSelectorWheel(false);
            String[] values = new String[18];
            for (int i = 0; i < values.length; i++) {
                String number = Integer.toString((i+1)*10);
                values[i] = number;
            }
            mNumberPicker.setDisplayedValues(values);

            mNumberPicker.setOnValueChangedListener(new NumberPicker.OnValueChangeListener() {
                @Override
                public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                    duration = Integer.valueOf(values[newVal]);
                }
            });



            return new AlertDialog.Builder(getActivity())
                    .setView(v)
                    .setTitle("Change duration")
                    .setPositiveButton("Save",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {

                                    LocalBroadcastManager.getInstance(getActivity()).sendBroadcast(

                                            new Intent("settings")
                                                    .putExtra("duration", duration)
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

