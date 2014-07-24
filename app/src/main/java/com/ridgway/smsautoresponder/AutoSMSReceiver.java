package com.ridgway.smsautoresponder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.util.Log;

public class AutoSMSReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		//---get the SMS message passed in---
		Bundle bundle=intent.getExtras();
		if(bundle != null){
			
			Object[] pdus = (Object[])bundle.get("pdus");
		    SmsMessage shortMessage = SmsMessage.createFromPdu((byte[]) pdus[0]);
	
		    String sender = shortMessage.getOriginatingAddress();
		    String msg = shortMessage.getDisplayMessageBody();
		    //Log.d("SMSReceiver","SMS message sender: "+ sender);
		    //Log.d("SMSReceiver","SMS message text: "+ msg );
		    
		    //---send a broadcast intent to update the SMS received in the activity---
		    Intent broadcastIntent = new Intent();
		    broadcastIntent.setAction("SMS_RECEIVED_ACTION");broadcastIntent.putExtra("sms_number", sender); 
		    context.sendBroadcast(broadcastIntent);
		}
	}

}
