package com.ridgway.smsautoresponder;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdRequest.Builder;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesUtil;
import android.provider.ContactsContract;

import java.sql.Date;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;


public class MainActivity extends ActionBarActivity
                        implements DurationPickerDialog.DurationPickerDialogListener {

	private static final int mNotificationId = 42; // Responses Sent Notification Id
    Toast toastResponse = null;

	// Setup option for debugging or not
	// This can be used to conditionalize some functionality
	private boolean mDebug = false;
	private boolean mFreeVersion = true;
	
	// Setup member strings for main layout response
	// display
	String strDrive = "";
	String strBike = "";
	String strRun = "";
	String strHike = "";
	String strDisturb = "";
	String strDefaultActivity = "";
	String returnMessage = "";

    int responsesSent = 0;
    int mrepeat_delay = 5;

    boolean mStart = false;
    boolean mbenable_delay = true;
    boolean mbenable_known_contacts = false;
    boolean mbignore_short = true;
    boolean mbsilent_when_driving = true;

    boolean receiverRegistered = false;
	boolean googlePlayAvailable = false;
	boolean googleDialogShown = false;

    private PendingIntent pendingAlarmIntent;
    private AlarmManager alarmMgr;

    private SMSSQLiteHelper db;
    private ListView listView;
    private SMSCursorAdapter smsAdapter;

    /**
	 * Setup Broadcast Receiver for incoming SMS Messages
	 */
	IntentFilter intentFilter;
    private BroadcastReceiver intentReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
        	String smsNumber = intent.getExtras().getString("sms_number");
            Log.d("SMSAutoResponder", "SMS Received from: " + smsNumber );

            boolean bSendResponse = true;
            String msg = returnMessage;
            if(mFreeVersion){
                msg = returnMessage + getString(R.string.response_sentby);
            }

            if(bSendResponse && smsNumber.length() < 7 && mbignore_short) {
                // Create toast message
                context = getApplicationContext();
                CharSequence text = getResources().getString(R.string.short_ignore_toast);
                int duration = Toast.LENGTH_SHORT;
                Toast toastIgnoreShort = Toast.makeText(context, text, duration);
                toastIgnoreShort.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toastIgnoreShort.show();
                bSendResponse = false;

            }


            if(bSendResponse && mbenable_known_contacts){
                // validate incoming message number is in
                // the contacts list.
                boolean bFound = false;
                Uri lookupUri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                        Uri.encode(smsNumber));

                ContentResolver localContentResolver = getApplicationContext().getContentResolver();
                Cursor contactLookupCursor =
                        localContentResolver.query(
                                lookupUri,
                                new String[] {ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup._ID},
                                null,
                                null,
                                null);
                try {
                    while(contactLookupCursor.moveToNext()){
                        String contactName = contactLookupCursor.getString(contactLookupCursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME));
                        String contactId = contactLookupCursor.getString(contactLookupCursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID));
                        Log.d("SMSAutoResponder", "contactMatch name: " + contactName);
                        Log.d("SMSAutoResponder", "contactMatch id: " + contactId);
                        bFound = true;

                    }
                } finally {
                    contactLookupCursor.close();
                }

                if(!bFound){
                    context = getApplicationContext();
                    CharSequence text = getResources().getString(R.string.unknown_ignore_toast);
                    int duration = Toast.LENGTH_SHORT;
                    Toast toastIgnoreUnknown = Toast.makeText(context, text, duration);
                    toastIgnoreUnknown.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                    toastIgnoreUnknown.show();
                    bSendResponse = false;
                }
            }


            // if we have an option selected to limit the response frequency,
            // let's get the most recent response for the current number and if it's
            // older than the cutoff, we can send the respsonse, otherwise we'll ignore
            // the incoming response
            if(bSendResponse && mbenable_delay){
                long recentMillis = db.getLatestResponseTime(smsNumber);
                if(recentMillis == 0){
                    Log.d("SMSAutoResponder", "No Recent response for number: " + smsNumber);
                }
                else{
                    long nowMillis = System.currentTimeMillis();

                    Date now = new Date(nowMillis);
                    Date smsDate = new Date(recentMillis);
                    Log.d("SMSAutoResponder", "Most Recent response for number: " + smsNumber + " is " + smsDate);
                    Log.d("SMSAutoResponser", "Repeat Delay set to " + mrepeat_delay + "minutes");

                    long diff = now.getTime() - smsDate.getTime();
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
                    Log.d("SMSAutoResponser", "Difference between now and recent response: " + minutes + "minutes");

                    if( minutes < mrepeat_delay){
                        bSendResponse = false;
                        Log.d("SMSAutoResponser", "Recent Response too recent. Not responding again so soon.");
                    }

                }
            }

            // If we have the right configuration,
            // Send a response to the incoming message.
            if(bSendResponse){
	        	// Return a response if sms number isn't one of those special short numbers
	            SmsManager smsManager = SmsManager.getDefault();
	            smsManager.sendTextMessage(smsNumber, null, msg, null, null);        
	            responsesSent++;

                // add to the database
                db.addResponse(smsNumber);
                smsAdapter.changeCursor(db.getAllData());

                createNotification();
	            toastResponse.show();
        	}
        }
    };

    /**
     * Receive Broadcasts to stop responses from Alarm
     */
    private BroadcastReceiver AlarmReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if(mDebug) {
                Toast.makeText(c, getString(R.string.auto_disable_response_msg), Toast.LENGTH_LONG).show();
            }
            Log.d("SMSAutoResponder", "Alarm Received. Stopping Responses" );
            stopResponses();
        }
    };


    /**
     * Methods used for Duration Picker Dialog and receiving
     * responses and retrieving values from the dialog.
     */
    public void showNoticeDialog() {
        // Create an instance of the dialog fragment and show it
        DialogFragment dialog = new DurationPickerDialog();
        dialog.show(getSupportFragmentManager(), "DurationPickerDialog");
    }

    // The dialog fragment receives a reference to this Activity through the
    // Fragment.onAttach() callback, which it uses to call the following methods
    // defined by the DurationPickerDialog.DurationPickerDialogListener interface
    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
        // Do nothing here. Just to satisfy onClickListener implementation
    }

    public void onDialogPositiveClick(DialogFragment dialog, SeekBar durationBar) {

        if(mDebug){
            // Create toast message
            CharSequence txt = "Main: Delay Dialog Positive Click. mstart: " + mStart;
            showDebugToast(txt);
        }

        // User touched the dialog's positive button, so we start handling
        // responses to incoming texts.
        responsesSent = 0;
        startResponses();

        // Set an alarm to broadcast when we should automatically
        // disable the responses.
        long seekBarTimeInMinutes = durationBar.getProgress();
        long now = System.currentTimeMillis();
        long alarmTime = now + (seekBarTimeInMinutes * 60 * 1000);

        // Setup the AlarmManager for auto disable of responses.
        registerReceiver(AlarmReceiver, new IntentFilter("com.ridgway.smsautoresponder.stopresponses") );
        pendingAlarmIntent = PendingIntent.getBroadcast( this, 0, new Intent("com.ridgway.smsautoresponder.stopresponses"),0 );
        alarmMgr = (AlarmManager)(this.getSystemService( Context.ALARM_SERVICE ));

        alarmMgr.set( AlarmManager.RTC, alarmTime, pendingAlarmIntent );
        Log.d("SMSAutoResponder", "Alarm Set for: " + seekBarTimeInMinutes + " minutes" );

    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button
        // invert the start option.
        if(mDebug){
            // Create toast message
            CharSequence txt = "Main: Delay Dialog Negative Click. mstart: " + mStart;
            showDebugToast(txt);
        }

    }



    /**
     * 
     * All of the override functions 
     * 
     * 
     */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

        // Get a handle to our database
        db = new SMSSQLiteHelper(this);

        // Database query can be a time consuming task ..
        // so its safe to call database query in another thread
        // Handler, will handle this stuff
        listView = (ListView) findViewById(R.id.listView);
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                smsAdapter = new SMSCursorAdapter(MainActivity.this, db.getAllData());
                listView.setAdapter(smsAdapter);
            }
        });

        // Get Preferences
		getSavedPrefs();
		
		//---intent to filter for SMS messages received---
		intentFilter = new IntentFilter();
		intentFilter.addAction("SMS_RECEIVED_ACTION");
		
		ActivateButtons(receiverRegistered);		
		updateResponseCount();

		// Setup click listeners so we can have two actions from the enable/disable button
		Button enableBtn = (Button) findViewById(R.id.btnStart);
	    enableBtn.setOnClickListener(new View.OnClickListener() {
	        public void onClick(View v) {
	            shortClickEnableBtn(v);
	        }
	     });
	
	    enableBtn.setOnLongClickListener(new View.OnLongClickListener() {
		    public boolean onLongClick(View v) {
		        longClickEnableBtn(v);
		        return true;
	    }});

        // Look up the AdView as a resource and load a request.
	    AdView adView = (AdView)this.findViewById(R.id.adView);
	    Builder adBuilder = new AdRequest.Builder();
	    
	    if(mDebug){
	    	adBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);       // Emulator
		    adBuilder.addTestDevice("B3EEABB8EE11C2BE770B684D95219ECB"); // My Galaxy Nexus Virtual Device
	    }
	    AdRequest adRequest = adBuilder.build();
	    adView.loadAd(adRequest);
	    
	    // Create toast message
	    Context context = getApplicationContext();
	    CharSequence text = getResources().getString(R.string.response_toast);
	    int duration = Toast.LENGTH_SHORT;
	    toastResponse = Toast.makeText(context, text, duration);
	    toastResponse.setGravity(Gravity.CENTER_VERTICAL, 0, 0);

		if(mDebug){
		    // Create toast message
		    CharSequence txt = "receiverRegistered: " + receiverRegistered + " mStart: " + mStart
		    			+ " responsesSent: " + responsesSent;
		    showDebugToast(txt);
		}

	}


	@Override
	protected void onPause(){
        super.onPause();
        saveSpinner();
	}

	@Override
	protected void onResume(){
        super.onResume();

    	googlePlayAvailable = false;

    	Context context = getApplicationContext();
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
        if (resultCode == ConnectionResult.SUCCESS){
        	googlePlayAvailable = true;
        	googleDialogShown = false;
        }
        else if(( resultCode == ConnectionResult.SERVICE_MISSING ||
                resultCode == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED ||
                resultCode == ConnectionResult.SERVICE_DISABLED) && !googleDialogShown){
            int requestCode = 10;
            Dialog dialog = GooglePlayServicesUtil.getErrorDialog(resultCode, this, requestCode);
            dialog.show();
            googleDialogShown = true;
        }
        else{
    	    String text = getResources().getString(R.string.problem_dlg_msg);
            showOkDialogWithText(this, text);
        }
        
		if(mStart){
			startResponses();
		}

		if(mDebug){
		    // Create toast message
		    CharSequence txt = "receiverRegistered: " + receiverRegistered + " mStart: " + mStart
		    			+ " responsesSent: " + responsesSent;
		    showDebugToast(txt);
		}

	}


	@Override
	protected void onStop(){
        super.onStop();
        saveSpinner();
	}
        
	@Override
	protected void onDestroy(){

        // cleanup the AlarmManager and Alarm Broadcast Receiver
        if(alarmMgr != null) {
            alarmMgr.cancel(pendingAlarmIntent);
            unregisterReceiver(AlarmReceiver);
        }
        
        super.onDestroy();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
        if (id == R.id.action_preferences) {
            openPreferences();
            return true;
        }
		if (id == R.id.action_exit) {
			new AlertDialog.Builder(this)
	    	.setTitle(R.string.acknowledgeexit_title )
	    	.setMessage(R.string.acknowledgeexit_msg)
	    	.setPositiveButton(R.string.dlg_yes, new OnClickListener() {
		    	public void onClick(DialogInterface arg0, int arg1) {
		    		//do stuff onclick of YES
					stopReciever();
					// now exit the application and unload from memory
					finish();
		    	}
		    })
	    	.setNegativeButton(R.string.dlg_cancel, new OnClickListener() {
		    	public void onClick(DialogInterface arg0, int arg1) {
			    	//do nothing onclick of CANCEL
		    	}
	    	}).show();
			
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * 
	 * All the private class methods & callback methods
	 * 
	 * 
	 */

    // Dialog for confirming Exit App
    public static void showOkDialogWithText(Context context, String messageText)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(messageText);
        builder.setCancelable(true);
        builder.setPositiveButton("OK", null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Toast to show debug messages
    public void showDebugToast(CharSequence txt){

        Context context = getApplicationContext();
        int dur = Toast.LENGTH_LONG;
        Toast toastDebug = Toast.makeText(context, txt, dur);
        toastDebug.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toastDebug.show();
    }


    // unresgister the broadcast receiver for SMS messages
    // and disable notifications.
    private void stopReciever(){
		// cancel the start
		mStart = false;
		
        //---unregister the receiver---  
        if (receiverRegistered){
	    	unregisterReceiver(intentReceiver);   
	    	receiverRegistered = false;
        }
        
        // Cancel the notifications
    	NotificationManager mNotificationManager =
        	    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

    	mNotificationManager.cancelAll();

        // Cancel any alarms
        if(alarmMgr != null) {
            alarmMgr.cancel(pendingAlarmIntent);
        }

    }
	
	/**
	 * Update the response text based on the spinner selection
	 */
	private void updateResponse(){
		
		String strActDrive = getResources().getString(R.string.activity_driving);
		String strActBike = getResources().getString(R.string.activity_cycling);
		String strActRun = getResources().getString(R.string.activity_running);
		String strActHike = getResources().getString(R.string.activity_hiking);
		String strActDisturb = getResources().getString(R.string.activity_donotdisturb);
			
        // Get the selected value from the spinner
		Spinner spinActivity = (Spinner) findViewById(R.id.spinner1);
		String selectedActivity = spinActivity.getItemAtPosition(spinActivity.getSelectedItemPosition()).toString();

			
		TextView responseText = (TextView) findViewById(R.id.TextViewResponseDefault);
		if (selectedActivity.compareToIgnoreCase(strActHike) == 0 ){
			responseText.setText(strHike);
			returnMessage = strHike;
		}
		else if (selectedActivity.compareToIgnoreCase(strActBike) == 0 ){
			responseText.setText(strBike);
			returnMessage = strBike;
		}
		else if (selectedActivity.compareToIgnoreCase(strActRun) == 0 ){
			responseText.setText(strRun);
			returnMessage = strRun;
		}
		else if (selectedActivity.compareToIgnoreCase(strActDrive) == 0 ){
			responseText.setText(strDrive);
			returnMessage = strDrive;
		}
		else if (selectedActivity.compareToIgnoreCase(strActDisturb) == 0 ){
			responseText.setText(strDisturb);
			returnMessage = strDisturb;
		}
		else{
			responseText.setText("");
			returnMessage = "";
		}

	}
	
	/**
	 * Show the count of recent responses
	 */
	private void updateResponseCount(){
		// Update the response count in the UI only for Debug setups.
		// Hide otherwise. Response count shows in Notifications.
		TextView txtCount = (TextView) findViewById(R.id.TextViewResponseCountDisplay);
		txtCount.setText(String.valueOf(responsesSent));		
		txtCount.setVisibility(mDebug ? View.VISIBLE : View.INVISIBLE);
		
		TextView txtResponseCountTitle = (TextView) findViewById(R.id.TextViewResponseCountTitle);
		txtResponseCountTitle.setVisibility(mDebug ? View.VISIBLE : View.INVISIBLE);
	}
	

	/**
	 * Select the Spinner entry by value, rather than position
	 */
	public static void SelectSpinnerItemByValue(Spinner spnr, String value)
	{
		@SuppressWarnings("unchecked")
		ArrayAdapter<String> adapter = (ArrayAdapter<String>) spnr.getAdapter();
	    spnr.setSelection(adapter.getPosition(value));
	}


    /**
     * Respond to the preferences menu item
     */
    public void openPreferences(){
        // Open the settings panel
        Intent intent = new Intent(this, PrefsActivity.class);
        startActivity(intent);
    }


	/**
	 * return the current value of the response message
	 * @return
	 */
	public String getResponseMessage(){
		TextView responseText = (TextView) findViewById(R.id.TextViewResponseDefault);
		String strResponse = responseText.getText().toString();
		return strResponse;
	}

    /**
     * In response to the Enable/Disable button short click.
     * Only enable on short click, if the responses are disabled.
     * Otherwise, if currently enabled, popup a toast informing
     * the user that they need to long click to disable.
     *
     * The long click is used to eliminate inadvertent disabling.
     *
     * @param v
     */
	public void shortClickEnableBtn(View v){
		if(!mStart){
			toggleResponses(v);
		}
		else{
		    // Create toast message
		    Context context = getApplicationContext();
		    CharSequence text = getResources().getString(R.string.longclick_toast);
		    int duration = Toast.LENGTH_SHORT;
		    Toast toastClick = Toast.makeText(context, text, duration);
		    toastClick.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
		    toastClick.show();
		}
	}

    /**
     * Use a long click to disable responses, if they're enabled.
     * @param v
     */
	public void longClickEnableBtn(View v){
		if(mStart){
			toggleResponses(v);
		}
	}
	
	
    /** Called when the user clicks the Start button */
    public void toggleResponses(View view) {

    	if(mStart){
            mStart = false;
            stopResponses(view);
    	}
    	else{
            startResponses(view);
    	}
    }
	
    /** Called when the user clicks the Start button */
    public void startResponses(View view) {
        // Show the duration picker dialog that allows
        // choosing an auto disable timespan.
        // responses are started from the dialog listener
        showNoticeDialog();

    }


    /**
     * Enable the broadcast receiver for SMS Messages,
     * Activate the buttons and toggle the ringer mode, if the option is selected.
     * Create the notifications and update the responses count.
     *
     */
    private void startResponses() {

        Log.d("SMSAutoResponder", "startResponses" );
        if(mDebug){
            // Create toast message
            CharSequence txt = "Start Responses";
            showDebugToast(txt);
        }

        mStart = true;

        //---register the receiver---
        registerReceiver(intentReceiver, intentFilter);
        receiverRegistered = true;

        ActivateButtons(receiverRegistered);

        updateResponseCount();
        createNotification();


        if(mbsilent_when_driving) {
            toggleRingerMode(true);
        }
    }
    
    /** Called when the user clicks the Stop button */
    public void stopResponses(View view) {
        stopResponses();
    }

    private void stopResponses(){
        Log.d("SMSAutoResponder", "stopResponses" );
    	stopReciever();
    	ActivateButtons(receiverRegistered);

        if(mbsilent_when_driving) {
            toggleRingerMode(false);
        }
    }


    /**
     * Toggle the ringer mode, so we can silence the phone ringer
     * if we're in driving mode and the preference option is selected.
     * @param bSilent
     */
    private void toggleRingerMode(boolean bSilent){

        int mode = AudioManager.RINGER_MODE_NORMAL;
        if(bSilent){
            mode = AudioManager.RINGER_MODE_SILENT;
        }
        // Get the selected value from the spinner
        Spinner spinActivity = (Spinner) findViewById(R.id.spinner1);
        String selectedActivity = spinActivity.getItemAtPosition(spinActivity.getSelectedItemPosition()).toString();
        String strActDrive = getResources().getString(R.string.activity_driving);
        if (selectedActivity.compareToIgnoreCase(strActDrive) == 0 ) {
            AudioManager audiomanager = (AudioManager) MainActivity.this.getSystemService(Context.AUDIO_SERVICE);
            audiomanager.setRingerMode(mode);
        }

    }

    /**
     * Enable/Disable start/stop buttons
     * Disable spinner when active, so the message option
     * can't be changed while running.
     * 
     * @param bStarted
     */
    private void ActivateButtons(boolean bStarted){
    	    	
		Button btnStart = (Button) findViewById(R.id.btnStart);
		Spinner spinActivity = (Spinner) findViewById(R.id.spinner1);

		// Update Button Text
		if(bStarted){
			btnStart.setText(getResources().getString(R.string.disable));
			spinActivity.setEnabled(false);
		}
		else{
			btnStart.setText(getResources().getString(R.string.enable));
			spinActivity.setEnabled(true);
		}
    }


    /**
     * Create the notifications for the application
     */
    private void createNotification(){
    	
    	String strNotificationsSent = getResources().getString(R.string.notifications_sent) + " " + String.valueOf(responsesSent);
    	NotificationCompat.Builder mBuilder =
    	        new NotificationCompat.Builder(this)
    	        .setSmallIcon(R.drawable.ic_notification)
    	        .setContentTitle(getResources().getString(R.string.notification_title))
    	        .setAutoCancel(true)
    	        .setOngoing(true)
    	        .setTicker(strNotificationsSent)
    	        .setContentText(strNotificationsSent);
    	
    	// Creates an explicit intent for an Activity in your app
    	Intent resultIntent = new Intent(this, MainActivity.class);

		// Set flags to reuse intent if it still exists
    	resultIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);

    	// The stack builder object will contain an artificial back stack for the
    	// started Activity.
    	// This ensures that navigating backward from the Activity leads out of
    	// your application to the Home screen.
    	TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
    	
    	// Adds the back stack for the Intent (but not the Intent itself)
    	stackBuilder.addParentStack(MainActivity.class);
    	
    	// Adds the Intent that starts the Activity to the top of the stack
    	stackBuilder.addNextIntent(resultIntent);
    	
    	PendingIntent resultPendingIntent =
    	        stackBuilder.getPendingIntent(
    	            0,
    	            PendingIntent.FLAG_UPDATE_CURRENT
    	        );
    	mBuilder.setContentIntent(resultPendingIntent);
    	
    	NotificationManager mNotificationManager =
    	    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
    	
    	// mId allows you to update the notification later on.
    	mNotificationManager.notify(mNotificationId, mBuilder.build());
    	
    }

    /**
     * Get the Preferences from the default shared preferences file.
     */
    public void getSavedPrefs(){
		// If we have previously saved preferences, then take those strings and use them
		SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

		String defaultDrive = getResources().getString(R.string.response_driving);
		strDrive = sharedPref.getString(getString(R.string.saved_response_driving), defaultDrive);
		
		String defaultBike = getResources().getString(R.string.response_cycling);
		strBike = sharedPref.getString(getString(R.string.saved_response_biking), defaultBike);
		
		String defaultRun = getResources().getString(R.string.response_running);
		strRun = sharedPref.getString(getString(R.string.saved_response_running), defaultRun);
		
		String defaultHike = getResources().getString(R.string.response_hiking);
		strHike = sharedPref.getString(getString(R.string.saved_response_hiking), defaultHike);

		String defaultDisturb = getResources().getString(R.string.response_donot_disturb);
		strDisturb = sharedPref.getString(getString(R.string.saved_response_donotdisturb), defaultDisturb);

		String defaultActivity = getResources().getString(R.string.default_activity);
		strDefaultActivity = sharedPref.getString(getString(R.string.saved_activity_option), defaultActivity);

		Spinner spinActivity = (Spinner) findViewById(R.id.spinner1);
		
		// Setup an listener on the spinner, so we can update the response when the user makes
		// a change to their selected activity.
		spinActivity.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
		    public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) { 
		    	 updateResponse();
		    } 

		    public void onNothingSelected(AdapterView<?> adapterView) {
		        return;
		    } 
		});		

		SelectSpinnerItemByValue(spinActivity, strDefaultActivity);

	    // Get response count and start flag
		responsesSent = sharedPref.getInt(getString(R.string.saved_response_count), 0);
		mStart = sharedPref.getBoolean(getString(R.string.saved_response_start), false);

        SharedPreferences settingsPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        mrepeat_delay = settingsPrefs.getInt(getString(R.string.saved_repeat_delay), 5);
        mbenable_delay = settingsPrefs.getBoolean(getString(R.string.saved_enable_delay), false);
        mbenable_known_contacts = settingsPrefs.getBoolean(getString(R.string.saved_enable_known_contacts), false);
        mbignore_short = settingsPrefs.getBoolean(getString(R.string.saved_ignore_short), true);
        mbsilent_when_driving = settingsPrefs.getBoolean(getString(R.string.saved_silent_when_driving), false);


    }

    /**
     * Save the Spinner Selection to the shared preferences
     */
    private void saveSpinner(){

        // Get the selected value from the spinner
        Spinner spinActivity = (Spinner) findViewById(R.id.spinner1);
        String selectedActivity = spinActivity.getItemAtPosition(spinActivity.getSelectedItemPosition()).toString();

        // Get a handle to the shared preferences and an editor, so we can update them
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = settings.edit();

        // Save the option in the preferences
        String option = getString(R.string.saved_activity_option);
        editor.putString(option, selectedActivity);

        // Save the start setting in the preferences
        String savedResponseKey = getString(R.string.saved_response_start);
        editor.putBoolean(savedResponseKey, mStart);

        // Save the response count in the preferences
        String savedCountKey = getString(R.string.saved_response_count);
        editor.putInt(savedCountKey, responsesSent);

        editor.apply();
        editor.commit();

    }


}
