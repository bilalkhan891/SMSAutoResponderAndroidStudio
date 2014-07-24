package com.ridgway.smsautoresponder;

import android.os.Bundle;
import android.preference.PreferenceFragment;


public class SettingsPanelFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_panel);
    }

}
