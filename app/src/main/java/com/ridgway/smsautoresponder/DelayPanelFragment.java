package com.ridgway.smsautoresponder;

/**
 * Created by ridgway on 7/29/14.
 */


import android.os.Bundle;
import android.preference.PreferenceFragment;


public class DelayPanelFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.delay_panel);
    }

}
