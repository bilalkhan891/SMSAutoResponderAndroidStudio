package com.ridgway.smsautoresponder;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import java.util.List;

/**
 * Created by ridgway on 7/3/14.
 */
public class PrefsActivity extends PreferenceActivity {

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        // add the xml resource
        // addPreferencesFromResource(R.xml.user_settings);


    }

    @Override
    public boolean isValidFragment(String fragmentName){
        return true;
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.prefs_headers, target);
    }
}