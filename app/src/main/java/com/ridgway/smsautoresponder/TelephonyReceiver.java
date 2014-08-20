package com.ridgway.smsautoresponder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.lang.reflect.Method;

/**
 * Created by ridgway on 8/19/14.
 */


public class TelephonyReceiver extends BroadcastReceiver {
    Context context = null;
    private static final String TAG = "Phone call";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!intent.getAction().equals("android.intent.action.PHONE_STATE"))
            return;

        Log.v(TAG, "Receving a call....");
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String state = extras.getString(TelephonyManager.EXTRA_STATE);
            if (state.equals(TelephonyManager.EXTRA_STATE_RINGING)) {
                String phoneNumber = extras.getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
                Log.w("TelephonyReceiver: incoming number", phoneNumber);

                //---send a broadcast intent to update the SMS received in the activity---
                Intent broadcastIntent = new Intent();
                broadcastIntent.setAction("CALL_RECEIVED_ACTION");
                broadcastIntent.putExtra("sms_number", phoneNumber);

                context.sendBroadcast(broadcastIntent);
            }
        }

    }
}