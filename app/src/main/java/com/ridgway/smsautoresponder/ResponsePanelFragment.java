package com.ridgway.smsautoresponder;

import android.os.Bundle;
import android.preference.PreferenceFragment;


public class ResponsePanelFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.responses_panel);
    }

}
