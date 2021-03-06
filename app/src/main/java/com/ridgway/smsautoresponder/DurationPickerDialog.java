package com.ridgway.smsautoresponder;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.text.SimpleDateFormat;


/**

 */
public class DurationPickerDialog extends DialogFragment {

    private static boolean mDebug = false;
    private Context mContext;

    // Toast to show debug messages
    public void showDebugToast(CharSequence txt){

        if(mDebug == false){
            // Get out of here, if we're not debugging.
            return;
        }

        Toast toastDebug = Toast.makeText(mContext, txt, Toast.LENGTH_LONG);
        toastDebug.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toastDebug.show();
    }


    /* The activity that creates an instance of this dialog fragment must
     * implement this interface in order to receive event callbacks.
     * Each method passes the DialogFragment in case the host needs to query it. */
    public interface DurationPickerDialogListener {
        public void onDialogPositiveClick(DialogFragment dialog);
        public void onDialogNegativeClick(DialogFragment dialog);
    }

    // Use this instance of the interface to deliver action events
    DurationPickerDialogListener mListener;
    private final DialogFragment dialogFrag = this;


    // Override the Fragment.onAttach() method to instantiate the DurationPickerDialogListener
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        // Verify that the host activity implements the callback interface
        try {
            // Instantiate the DurationPickerDialogListener so we can send events to the host
            mListener = (DurationPickerDialogListener) activity;
            mContext = activity.getApplicationContext();
        } catch (ClassCastException e) {
            // The activity doesn't implement the interface, throw exception
            throw new ClassCastException(activity.toString()
                    + " must implement DurationPickerDialogListener");
        }
    }


    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(mContext);
        Integer autoDisableDelay = sharedPref.getInt(getString(R.string.saved_auto_disable_response_delay), 60);



        // Use the Builder class for convenient dialog construction
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Get the layout inflater
        LayoutInflater inflater = getActivity().getLayoutInflater();

        // Inflate and set the layout for the dialog
        // Pass null as the parent view because its going in the dialog layout
        View view = inflater.inflate(R.layout.fragment_duration_picker_dialog, null);
        builder.setView(view);
        builder.setTitle(R.string.duration_picker_title);

        final SeekBar durationBar = (SeekBar) view.findViewById(R.id.seekBar);
        final Button btnMinus = (Button)view.findViewById(R.id.buttonMinus);
        final Button btnPlus = (Button)view.findViewById(R.id.buttonPlus);

        btnMinus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // Decrease duration by 10% of Max value
                durationBar.setProgress(durationBar.getProgress() - durationBar.getMax()/10);
            }
        });
        btnPlus.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // Increase duration by 10% of Max value
                durationBar.setProgress(durationBar.getProgress() + durationBar.getMax()/10);
            }
        });

        durationBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progressChanged = 0;

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser){
                progressChanged = progress;
                View view = seekBar.getRootView();
                TextView delayText = (TextView) view.findViewById(R.id.textDuration);
                TextView delayUntilText = (TextView) view.findViewById(R.id.textTimeDisable);

                String dealyHrsMins = ConvertMinsToHrsMins(progress);
                String dealyTimeStop = ConvertMinsToStopTime(progress);

                if(seekBar.getMax() == progress){
                    // We're at the Max, so we want to NOT setup an alarm
                    // So, let's change the display text to indicate
                    dealyHrsMins = getResources().getString(R.string.disable_responses_never);
                    dealyTimeStop = getResources().getString(R.string.disable_responses_apocalypse);
                }

                delayText.setText(dealyHrsMins);
                delayUntilText.setText(dealyTimeStop);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                // Don't need to do anything here
            }

            public void onStopTrackingTouch(SeekBar seekBar) {
                showDebugToast("seek bar progress:" + progressChanged);
            }
        });
        durationBar.setProgress(autoDisableDelay);

        builder.setPositiveButton(R.string.enable, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Enable Timer and Responses!
                if (mDebug) {
                    // Create toast message
                    CharSequence txt = "Dlg: Delay Dialog Negative Click.";
                    showDebugToast(txt);
                }
                ((MainActivity) getActivity()).onDialogPositiveClick(dialogFrag, durationBar);
            }
        });
        builder.setNegativeButton(R.string.dlg_cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // User cancelled the dialog
                if (mDebug) {
                    // Create toast message
                    CharSequence txt = "Dlg: Delay Dialog Negative Click.";
                    showDebugToast(txt);
                }
                ((MainActivity) getActivity()).onDialogNegativeClick(dialogFrag);
            }
        });
        // Create the AlertDialog object and return it
        return builder.create();
    }

    private String ConvertMinsToHrsMins(int minutes){

        int hours = minutes/60;
        minutes = minutes - (hours * 60);
        String result = String.format("%02d %s %02d %s", hours, getString(R.string.hrs), minutes, getString(R.string.mins) );

        return result;
    }

    private String ConvertMinsToStopTime(int minutes){

        long now = System.currentTimeMillis();
        long newTime = now + (minutes * 60 * 1000);

        DateFormat timeString = DateFormat.getTimeInstance(DateFormat.SHORT);
        return timeString.format(newTime);

    }


}