package com.ridgway.smsautoresponder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

/**
 * Created by ridgway on 7/12/14.
 */
public class AlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context c, Intent i) {
        Toast.makeText(c, c.getApplicationContext().getString(R.string.auto_disable_response_msg), Toast.LENGTH_LONG).show();
    }

}
