package com.ridgway.smsautoresponder;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.NumberPicker;


public class NumberPickerPreference extends DialogPreference {
	
	private static int DEFAULT_VALUE = 5;
	private int mNewValue;
	private int mCurrentValue;
	
	private NumberPicker numPicker;
	
    public NumberPickerPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        
        setDialogLayoutResource(R.layout.numberpicker_dialog);
        setPositiveButtonText(android.R.string.ok);
        setNegativeButtonText(android.R.string.cancel);
        
        setDialogIcon(null);
        
    }

    @Override
    protected void onBindDialogView(View view) {

    	numPicker = (NumberPicker) view.findViewById(R.id.numberPicker1);
    	numPicker.setMaxValue(100);
    	numPicker.setMinValue(0);
    	numPicker.setValue(mCurrentValue);

        super.onBindDialogView(view);
    }


    @Override
    protected void onDialogClosed(boolean positiveResult) {
        // When the user selects "OK", persist the new value
        if (positiveResult) {
            mNewValue = numPicker.getValue();
            persistInt(mNewValue);
        }
    }
    
    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        if (restorePersistedValue) {
            // Restore existing state
            mCurrentValue = this.getPersistedInt(DEFAULT_VALUE);
        } else {
            // Set default state from the XML attribute
            mCurrentValue = (Integer) defaultValue;
            persistInt(mCurrentValue);
        }
    
    }
    
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return a.getInteger(index, DEFAULT_VALUE);
    }
    
}