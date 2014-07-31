package com.ridgway.smsautoresponder;

import android.os.Bundle;
import android.preference.PreferenceFragment;

/**
 * Created by ridgway on 7/31/14.
 */
public class DisplayPanelFragment extends PreferenceFragment {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.display_panel);
    }

}
