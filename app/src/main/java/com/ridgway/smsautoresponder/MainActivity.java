package com.ridgway.smsautoresponder;

import android.app.Activity;
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
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.ContactsContract;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v7.app.ActionBarActivity;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdRequest.Builder;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.ActivityRecognitionClient;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;


public class MainActivity extends ActionBarActivity
                        implements DurationPickerDialog.DurationPickerDialogListener,
                                    GooglePlayServicesClient.ConnectionCallbacks,
                                    GooglePlayServicesClient.OnConnectionFailedListener,
                                    TextToSpeech.OnInitListener {

	private static final int mNotificationId = 42; // Responses Sent Notification Id
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

	// Setup option for debugging or not
	// This can be used to conditionalize some functionality
	private boolean mDebug = false; // debug mode? Enables more toast message popups. Change the false for shipping.
	private boolean mFreeVersion = true; // Currently only have a free version, but just in case.
    private static final int SHORT_NUMBER_MAX_LENGTH = 7; // Max length of incoming message number that is considered short.
    private static final int DEFAULT_RESPONSE_REPEAT_DELAY = 5; // default delay between responses to the same number (in minutes)

    /**
     * Activity Recognition member variables
     */

    // Constants that define the activity detection interval
    public static final int MILLISECONDS_PER_SECOND = 1000;
    public static final int DETECTION_INTERVAL_SECONDS = 20;
    public static final int DETECTION_INTERVAL_MILLISECONDS =
            MILLISECONDS_PER_SECOND * DETECTION_INTERVAL_SECONDS;


    // Classify the type of Activity Recognition request we're operating on
    public enum REQUEST_TYPE {START, STOP}
    private REQUEST_TYPE mRequestType;

    /*
     * Store the PendingIntent used to send activity recognition events
     * back to the app
     */
    private PendingIntent mActivityRecognitionPendingIntent;
    // Store the current activity recognition client
    private ActivityRecognitionClient mActivityRecognitionClient;

    // Flag that indicates if a Activity Recognition request is underway.
    private boolean mInProgress;


    Context mContext;

	// Setup member strings for main layout response
	// display
    private String strDrive = "";
    private String strBike = "";
    private String strRun = "";
    private String strHike = "";
    private String strDisturb = "";
    private String strDefaultActivity = "";
    private String returnMessage = "";

    private int responsesSent = 0; // keep a count of the responses sent this time
    private int mrepeat_delay = 5; // default # minutes between successive responses to the same number

    private boolean mStart = false; // are we started
    private boolean mbenable_delay = true; // Is the repeat response delay enabled
    private boolean mbenable_known_contacts = false; // do we only reply to known contacts
    private boolean mbignore_short = true; // do we ignore texts from short numbers
    private boolean mbsilent_when_driving = true; // do we silence the phone ringer in driving mode
    private boolean mclear_data_on_exit = false; // clear the response data history on app exit
    private boolean mshow_responses_on_main_activity = true; // show the recent responses on the main activity screen
    private boolean mtrack_using_location_services = false; //use location services to determine current activity
    private boolean menable_tts_read_sms = false; //read incoming text messages using text-to-speech
    private boolean receiverRegistered = false; // Is our broadcast receiver registered
    private boolean googlePlayAvailable = false; // Are Google Play services available on the device
    private boolean googleDialogShown = false; // Have we already shown the Missing Google Play dialog

    private PendingIntent pendingAlarmIntent; // Intent for use with AlarmManager
    private AlarmManager alarmMgr; // AlarmManager used for automatic disable of responses after certain length of time

    private SMSSQLiteHelper db; // Database link for storing response information
    private ListView listView; // Main activity ListView to display recent responses
    private SMSCursorAdapter smsAdapter; // Adapter between the database and ListView

    private TextToSpeech mTts; // Instance of Text to Speech engine
    private static final int MY_DATA_CHECK_CODE = 1701;
    private boolean mTTS_available = false;

    /**
     * Setup a Broadcast Receiver for incoming Location Awareness
     * Activity Recognition state changes.
     *
     */
    IntentFilter activityRecognitionIntentFilter;
    private BroadcastReceiver activityRecognitionIntentReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {

            String strNewActivity = intent.getExtras().getString("detected_activity");

            if(strNewActivity.compareToIgnoreCase(getString(R.string.activity_unknown)) == 0 ||
                    strNewActivity.compareToIgnoreCase(getString(R.string.activity_tilting)) == 0 ){
                // If it's an unknown activity, we don't want to change anything.
                // Ignore the update completely.
                Log.d("activityRecognitionIntentReceiver", "onReceive: Detected " + strNewActivity + " activity. Ignore!" );
                return;
            }

            if(strNewActivity.compareToIgnoreCase(getString(R.string.activity_stationary)) == 0){
                Log.d("activityRecognitionIntentReceiver", "onReceive: Detected STILL activity. Switch to Do Not Disturb Msg" );
                strNewActivity = getString(R.string.activity_donotdisturb);
            }

            // if it's not an unknown update and we're currently
            // using location services to determine the activity,
            // then go ahead and make the change.
            if(mtrack_using_location_services) {
                // Get the Spinner and update its value
                Spinner spinActivity = (Spinner) findViewById(R.id.spinnerActivity);
                SelectSpinnerItemByValue(spinActivity, strNewActivity);

                // Update the current response.
                updateResponse();
            }
        }
    };



    /**
	 * Setup Broadcast Receiver for incoming SMS Messages
	 */
	IntentFilter smsIntentFilter;
    private BroadcastReceiver smsIntentReceiver = new BroadcastReceiver(){

        @Override
        public void onReceive(Context context, Intent intent) {
        	String smsNumber = intent.getExtras().getString("sms_number");
            String smsMsg = intent.getExtras().getString("sms_msg");
            Log.d("SMSAutoResponder", "SMS Received from: " + smsNumber );
            Log.d("SMSAutoResponder", "SMS Message: " + smsMsg );

            boolean bSendResponse = true;
            String msg = returnMessage;
            if(mFreeVersion){
                // Add extra text to outgoing messages
                // to advertise the App in the Free Version
                msg = returnMessage + getString(R.string.response_sentby);
            }

            // Check to see if we're ignoring short numbers and
            // compare the length of the incoming message number
            // Easiest thing to check, then we don't have to check anything
            // else if this kills the response.
            if(bSendResponse && mbignore_short && smsNumber.length() < SHORT_NUMBER_MAX_LENGTH) {
                // Create toast message
                context = getApplicationContext();
                CharSequence text = getResources().getString(R.string.short_ignore_toast);
                int duration = Toast.LENGTH_SHORT;
                Toast toastIgnoreShort = Toast.makeText(context, text, duration);
                toastIgnoreShort.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
                toastIgnoreShort.show();

                // Flag that we're not sending a response
                bSendResponse = false;

            }


            // If we're still OK to send the response, check if the number is
            // from a contact in our address book.
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

                    // Flag that we're not sending a response
                    bSendResponse = false;
                }
            }


            // If we're still OK to send the response, check
            // if we have an option selected to limit the response frequency,
            // let's get the most recent response for the current number and if it's
            // older than the cutoff, we can send the response, otherwise we'll ignore
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
                    SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SS");
                    String strDate = sdf.format(smsDate);
                    Log.d("SMSAutoResponder", "Most Recent response for number: " + smsNumber + " is " + strDate);
                    Log.d("SMSAutoResponser", "Repeat Delay set to " + mrepeat_delay + " minutes");

                    long diff = now.getTime() - smsDate.getTime();
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
                    Log.d("SMSAutoResponser", "Difference between now and recent response: " + minutes + " minutes");

                    if( minutes < mrepeat_delay){
                        // Flag that we're not sending a response
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
	            toastResponseSent();
        	}

            // if Read SMS is enabled, then try to read back the incoming message
            if(menable_tts_read_sms && mTTS_available){
                mTts.speak("Message from: " + smsNumber, TextToSpeech.QUEUE_ADD, null);
                mTts.speak(smsMsg, TextToSpeech.QUEUE_ADD, null);
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
            stopResponses(false);
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

        if(seekBarTimeInMinutes != durationBar.getMax()) {
            long now = System.currentTimeMillis();
            long alarmTime = now + (seekBarTimeInMinutes * 60 * 1000);

            // Setup the AlarmManager for auto disable of responses.
            registerReceiver(AlarmReceiver, new IntentFilter("com.ridgway.smsautoresponder.stopresponses"));
            pendingAlarmIntent = PendingIntent.getBroadcast(this, 0, new Intent("com.ridgway.smsautoresponder.stopresponses"), 0);
            alarmMgr = (AlarmManager) (this.getSystemService(Context.ALARM_SERVICE));

            alarmMgr.set(AlarmManager.RTC, alarmTime, pendingAlarmIntent);
            Log.d("SMSAutoResponder", "Alarm Set for: " + seekBarTimeInMinutes + " minutes");
        }
        else{
            Log.d("SMSAutoResponder", "seekBar set for MAX. Don't set an Alarm. User must Manually disable!");
        }
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
        // User touched the dialog's negative button
        if(mDebug){
            // Create toast message
            CharSequence txt = "Main: Delay Dialog Negative Click. mstart: " + mStart;
            showDebugToast(txt);
        }
        Log.d("SMSAutoResponder", "onDialogNegativeClick: Start Responses Canceled." );

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
        Log.d("SMSAutoResponder", "onCreate().");

		setContentView(R.layout.activity_main);

        mContext = getApplicationContext();

        // Set initial Location query Activity state
        mInProgress = false;

        // Get a handle to our database, so we can store/retrieve
        // recent responses.
        db = new SMSSQLiteHelper(this);

        // Database query can be a time consuming task ..
        // so its safe to call database query in another thread
        // Handler, will handle this stuff.
        // Start this early, so we can get everything else
        // up and running while this goes on. Limits the perceived delay.
        listView = (ListView) findViewById(R.id.listView);
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                smsAdapter = new SMSCursorAdapter(MainActivity.this, db.getAllData());
                listView.setAdapter(smsAdapter);
            }
        });

        // Add a header row to the listview
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup header = (ViewGroup) inflater.inflate(R.layout.listview_header, listView, false);
        listView.addHeaderView(header, null, false);

        // Get Preferences
		getSavedPrefs();

        // If Text to Speech is enabled, startup the engine
        if(menable_tts_read_sms) {
            Intent checkIntent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            startActivityForResult(checkIntent, MY_DATA_CHECK_CODE);
        }

        //---intent to filter for activity recognition updates received---
        activityRecognitionIntentFilter = new IntentFilter();
        activityRecognitionIntentFilter.addAction("ACTIVITY_RECOGNITION_UPDATE_ACTION");

		//---intent to filter for SMS messages received---
        smsIntentFilter = new IntentFilter();
        smsIntentFilter.addAction("SMS_RECEIVED_ACTION");
		
		ActivateButtons(receiverRegistered);

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
            // These are some debug settings, so we're not
            // using our live settings for debugging and possibly
            // giving out bad data for the Ad service.
	    	adBuilder.addTestDevice(AdRequest.DEVICE_ID_EMULATOR);       // Emulator
		    adBuilder.addTestDevice("B3EEABB8EE11C2BE770B684D95219ECB"); // My Galaxy Nexus Virtual Device
	    }
	    AdRequest adRequest = adBuilder.build();
	    adView.loadAd(adRequest);

		if(mDebug){
		    // Create toast message
		    CharSequence txt = "receiverRegistered: " + receiverRegistered + " mStart: " + mStart
		    			+ " responsesSent: " + responsesSent;
		    showDebugToast(txt);
		}

        // Toggle the response list on the main screen
        // depending on the preferences value.
        showResponsesList();

        googlePlayAvailable = servicesConnected();

        // Create the Activity Recognition Client
        // so we're ready when the user clicks enable.
        startActivityRecognitionClient();

	}


    /**
     * Handle Text to Speech OnInit.
     * @param status
     */
    @Override
    public void onInit(int status) {
        if(status == TextToSpeech.SUCCESS){
            mTTS_available = true;
        }
        else{
            mTTS_available = false;
        }
    }


    /**
     * Check the state of the Google Play Services on the device
     * just before the App is enabled and displayed.
     * Make sure the responses are started, if we were started previously.
     */
	@Override
	protected void onResume(){
        super.onResume();
        Log.d("SMSAutoResponder", "onResume().");

        // Check again, just in case something
        // has changed while we were away.x
        googlePlayAvailable = servicesConnected();

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

    /**
     * Save the current selection in the spinner when
     * moving to the Paused State
     */
    @Override
    protected void onPause(){
        super.onPause();
        Log.d("SMSAutoResponder", "onPause().");
        saveSpinner();
    }


    /**
     * Another instance where we make sure the Spinner state
     * is saved to our preferences file.
     */
	@Override
	protected void onStop(){
        super.onStop();
        Log.d("SMSAutoResponder", "onStop().");

	}

    /**
     * Clean up our Alarm & Receiver objects.
     */
	@Override
	protected void onDestroy(){
        super.onDestroy();
        Log.d("SMSAutoResponder", "onDestroy().");

        stopResponses(true);

        // cleanup the AlarmManager and Alarm Broadcast Receiver
        if(alarmMgr != null) {
            alarmMgr.cancel(pendingAlarmIntent);
            unregisterReceiver(AlarmReceiver);
        }

    }

    /**
     * Menu display callback.
     * @param menu
     * @return
     */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

    /**
     * Menu item selection callback.
     * @param item
     * @return
     */
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

        if (id == R.id.action_response_list) {
            openResponseList();
            return true;
        }

        if (id == R.id.action_activity_list) {
            openActivitiesList();
            return true;
        }

		if (id == R.id.action_exit) {
			new AlertDialog.Builder(this)
	    	.setTitle(R.string.acknowledge_exit_title )
	    	.setMessage(R.string.acknowledge_exit_msg)
	    	.setPositiveButton(R.string.dlg_yes, new OnClickListener() {
                public void onClick(DialogInterface arg0, int arg1) {
                    //do stuff onclick of YES
                    stopResponses();

                    // if the preferences are set for clearing data on exit,
                    // then execute that option.
                    if (mclear_data_on_exit) {
                        clearResponseData();
                        clearActivityData();
                    }

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
     * Handle Activity Awareness Connections
     * * Called by Location Services once the location client is connected.
     *
     * Continue by requesting activity updates.
     *
     * @param bundle
     */
    @Override
    public void onConnected(Bundle bundle){

        switch (mRequestType) {
            case START:
               /*
                 * Request activity recognition updates using the preset
                 * detection interval and PendingIntent. This call is
                 * synchronous.
                 */
                mActivityRecognitionClient.requestActivityUpdates(
                        DETECTION_INTERVAL_MILLISECONDS,
                        mActivityRecognitionPendingIntent);
                /*
                 * Since the preceding call is synchronous, turn off the
                 * in progress flag and disconnect the client
                 */
                mInProgress = false;
                mActivityRecognitionClient.disconnect();
                break;

            case STOP :
                mActivityRecognitionClient.removeActivityUpdates(
                                                mActivityRecognitionPendingIntent);
                break;

            default:
                Log.d("SMSAutoResponder", "onConnected: Unknown request type in onConnected().");
                break;


        }
    }


    /**
     * Handle Activity Awareness Disconnections
     *
     * Called by Location Services once the activity recognition
     * client is disconnected.
     *
     */
    @Override
    public void onDisconnected(){
        // Turn off the request flag
        mInProgress = false;
        // Delete the client
        mActivityRecognitionClient = null;

    }

    /**
     * Handle Activity Awareness Connection Failures
     *
     *  Implementation of OnConnectionFailedListener.onConnectionFailed
     *
     * @param connectionResult
     */
    @Override
    public void onConnectionFailed(ConnectionResult connectionResult){
        // Turn off the request flag
        mInProgress = false;
        /*
         * If the error has a resolution, start a Google Play services
         * activity to resolve it.
         */
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
            }
            catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
            // If no resolution is available, display an error dialog
        }
        else {
            // Get the error code
            int errorCode = connectionResult.getErrorCode();

            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                    errorCode,
                    this,
                    CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {

                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment = new ErrorDialogFragment();

                // Set the dialog in the DialogFragment
                errorFragment.setDialog(errorDialog);

                // Show the error dialog in the DialogFragment
                errorFragment.show( getSupportFragmentManager(),"Activity Recognition");
            }
        }

    }

    /**
	 * **************************************************
     *
	 * All the public class methods & callback methods
	 *
     * **************************************************
	 */

    // Dialog for confirming Exit App
    public static void showOkDialogWithText(Context context, String messageText, String btnOKText)
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setMessage(messageText);
        builder.setCancelable(true);
        builder.setPositiveButton(btnOKText, null);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    // Response Sent Toast Message
    public void toastResponseSent(){
        // Create our toast message
        Context context = getApplicationContext();
        CharSequence text = getResources().getString(R.string.response_toast);
        int duration = Toast.LENGTH_SHORT;
        Toast toastResponse = Toast.makeText(context, text, duration);
        toastResponse.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toastResponse.show();

    }

    // Toast to show debug messages
    public void showDebugToast(CharSequence txt){

        Context context = getApplicationContext();
        int dur = Toast.LENGTH_LONG;
        Toast toastDebug = Toast.makeText(context, txt, dur);
        toastDebug.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toastDebug.show();
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
     * Respond to the responses list menu item
     */
    public void openResponseList(){
        // Open the settings panel
        Intent intent = new Intent(this, SMSResponseListActivity.class);
        startActivity(intent);
    }

    /**
     * Respond to the Activities menu item
     */
    public void openActivitiesList(){
        // Open the settings panel
        Intent intent = new Intent(this, ActivityRecognitionListActivity.class);
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

    /**
     * Called when the user clicks the Start button
     */
    public void startResponses(View view) {
        // Show the duration picker dialog that allows
        // choosing an auto disable timespan.
        // responses are started from the dialog listener
        showNoticeDialog();

    }

    /**
     * Called when the user clicks the Stop button
     */
    public void stopResponses(View view) {
        stopResponses();
    }

    /**
     * **************************************************
     *
     * All the private class methods & callback methods
     *
     * **************************************************
     */

    // unregister the broadcast receiver for SMS messages
    // and disable notifications, when we're stopping responses.
    private void stopReceiver(){
		// cancel the start
		mStart = false;
		
        //---unregister the receiver---  
        if (receiverRegistered){
            unregisterReceiver(smsIntentReceiver);
            unregisterReceiver(activityRecognitionIntentReceiver);
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
     *
	 * Update the response text based on the spinner selection
	 */
	private void updateResponse(){
		
		String strActDrive = getResources().getString(R.string.activity_driving);
		String strActBike = getResources().getString(R.string.activity_cycling);
		String strActRun = getResources().getString(R.string.activity_running);
		String strActHike = getResources().getString(R.string.activity_hiking);
		String strActDisturb = getResources().getString(R.string.activity_donotdisturb);
			
        // Get the selected value from the spinner
		Spinner spinActivity = (Spinner) findViewById(R.id.spinnerActivity);
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

        //--- register the receivers for incoming SMS messages and
        // activity recognition from Location Services ---
        registerReceiver(smsIntentReceiver, smsIntentFilter);
        registerReceiver(activityRecognitionIntentReceiver, activityRecognitionIntentFilter);
        receiverRegistered = true;

        ActivateButtons(receiverRegistered);

        createNotification();

        if(mbsilent_when_driving) {
            toggleRingerMode(true);
        }

        // Start tracking the Google Play Services
        // Activity Recognition Updates from the system
        // We can use these to set the current activity
        // for determining the response to be sent
        // in reply to incoming text messages.
        startActivityRecognitionUpdates();
    }


    /**
     * Most places we call stopResponses we don't need to pass
     * a value, so we setup this one to pass false.
     * Only from onStop() do we bypass this method and
     * use true.
     */
    private void stopResponses(){
        stopResponses(false);
    }

    /**
     * Stop the responses, stop activity recognition, etc.
     *
     * @param onStop true if called from onStop()
     */
    private void stopResponses(boolean onStop){
        Log.d("SMSAutoResponder", "stopResponses" );
        stopReceiver();
    	ActivateButtons(receiverRegistered);

        if(mbsilent_when_driving) {
            toggleRingerMode(false);
        }

        // Stop tracking the Activity Recognition updates
        // from the Google Play Services location services.
        stopActivityRecognitionUpdates(onStop);
    }


    /**
     * Toggle the ringer mode, so we can silence the phone ringer
     * if we're in driving mode and the preference option is selected.
     * @param bSilent
     */
    private void toggleRingerMode(boolean bSilent){

        Log.d("SMSAutoResponder", "toggleRingerMode" );

        int mode = AudioManager.RINGER_MODE_NORMAL;
        if(bSilent){
            mode = AudioManager.RINGER_MODE_SILENT;
        }
        // Get the selected value from the spinner
        Spinner spinActivity = (Spinner) findViewById(R.id.spinnerActivity);
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
		Spinner spinActivity = (Spinner) findViewById(R.id.spinnerActivity);

		// Update Button Text
		if(bStarted){
			btnStart.setText(getResources().getString(R.string.disable));
			spinActivity.setEnabled(false);
		}
		else{
			btnStart.setText(getResources().getString(R.string.enable));
			spinActivity.setEnabled(true);
		}

        if(mtrack_using_location_services){
            spinActivity.setEnabled(false);
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
     * Get the Preferences from the default shared preferences file
     * and populate the private class member variables.
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

		Spinner spinActivity = (Spinner) findViewById(R.id.spinnerActivity);
		
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
        mrepeat_delay = settingsPrefs.getInt(getString(R.string.saved_repeat_delay), DEFAULT_RESPONSE_REPEAT_DELAY);
        mbenable_delay = settingsPrefs.getBoolean(getString(R.string.saved_enable_delay), false);
        mbenable_known_contacts = settingsPrefs.getBoolean(getString(R.string.saved_enable_known_contacts), false);
        mbignore_short = settingsPrefs.getBoolean(getString(R.string.saved_ignore_short), true);
        mbsilent_when_driving = settingsPrefs.getBoolean(getString(R.string.saved_silent_when_driving), false);
        mclear_data_on_exit = settingsPrefs.getBoolean(getString(R.string.saved_clear_data_on_exit), false);
        mshow_responses_on_main_activity = settingsPrefs.getBoolean(getString(R.string.saved_show_responses_on_main_activity), true);
        mtrack_using_location_services = settingsPrefs.getBoolean(getString(R.string.saved_track_using_location_services), false);
        menable_tts_read_sms = settingsPrefs.getBoolean(getString(R.string.saved_enable_tts_read_sms), false);
    }

    /**
     * Save the Spinner Selection to the shared preferences
     */
    private void saveSpinner(){

        // Get the selected value from the spinner
        Spinner spinActivity = (Spinner) findViewById(R.id.spinnerActivity);
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

    /**
     * Update the database to remove all the response history
     * and update the listview to reflect the new data
     */
    private void clearResponseData(){
        Log.d("SMSAutoResponder", "clearResponseData" );
        // clear the database
        db.deleteAllResponses();
        // update the listView
        smsAdapter.changeCursor(db.getAllData());
    }


    /**
     * Check if the Google Play Services are connected.
     *
     * We need this for Location Services and Ads.
     *
     * @return
     */
    private boolean servicesConnected(){

        Log.d("SMSAutoResponder", "servicesConnected" );

        boolean bAvailable = false;
        Context context = getApplicationContext();
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(context);
        if (resultCode == ConnectionResult.SUCCESS){
            // In debug mode, log the status
            Log.d("Activity Recognition",
                    "Google Play services is available.");
            bAvailable = true;
            googleDialogShown = false;
        }
        else {
            // Get the error dialog from Google Play services
            Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                    resultCode,
                    this,
                    CONNECTION_FAILURE_RESOLUTION_REQUEST);

            // If Google Play services can provide an error dialog
            if (errorDialog != null) {
                // Create a new DialogFragment for the error dialog
                ErrorDialogFragment errorFragment =
                        new ErrorDialogFragment();
                // Set the dialog in the DialogFragment
                errorFragment.setDialog(errorDialog);
                // Show the error dialog in the DialogFragment
                errorFragment.show(
                        getSupportFragmentManager(),
                        "Activity Recognition");
            }
            bAvailable = false;
        }

        return bAvailable;
    }

    /**
     *     Define a DialogFragment that displays the error dialog
     */
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog mDialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            mDialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            mDialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return mDialog;
        }
    }

    /**
     * Handle results returned to the FragmentActivity
     * by Google Play services
     *
     * Respond to startActivityForResult() call to setup Text to Speech
     *
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        // Decide what to do based on the original request code
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
            /*
             * If the result code is Activity.RESULT_OK, try
             * to connect again
             */
                switch (resultCode) {
                    case Activity.RESULT_OK :
                    /*
                     * Try the request again
                     */
                     servicesConnected();
                     break;
                }

            case MY_DATA_CHECK_CODE:
                if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                    // success, create the TTS instance
                    mTts = new TextToSpeech(getApplicationContext(), this);
                } else {
                    // missing data, install it
                    Intent installIntent = new Intent();
                    installIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    startActivity(installIntent);
                }
        }

    }

    /**
     * Request activity recognition updates based on the current
     * detection interval.
     *
     */
    public void startActivityRecognitionUpdates() {
        // Set the request type to START
        mRequestType = REQUEST_TYPE.START;

        Log.d("SMSAutoResponder", "startActivityRecognitionUpdates" );

        // Check for Google Play services

        if (!servicesConnected()) {
            return;
        }

        // If a request is not already underway
        if (!mInProgress) {
            if(mActivityRecognitionClient != null) {
                // Indicate that a request is in progress
                mInProgress = true;
                // Request a connection to Location Services
                mActivityRecognitionClient.connect();
            }
        } else {
            /*
             * A request is already underway. You can handle
             * this situation by disconnecting the client,
             * re-setting the flag, and then re-trying the
             * request.
             */
        }
    }

    private void startActivityRecognitionClient(){

        Log.d("SMSAutoResponder", "startActivityRecognitionClient" );

        if(!googlePlayAvailable){
            // We can't use this feature
            // if Google Play Services aren't available
            // so just return.
            return;
        }

        /*
         * Instantiate a new activity recognition client. Since the
         * parent Activity implements the connection listener and
         * connection failure listener, the constructor uses "this"
         * to specify the values of those parameters.
         */
        mActivityRecognitionClient = new ActivityRecognitionClient(mContext, this, this);

        /*
         * Create the PendingIntent that Location Services uses
         * to send activity recognition updates back to this app.
         */
        Intent intent = new Intent(
                mContext, ActivityRecognitionIntentService.class);

        /*
         * Return a PendingIntent that starts the IntentService.
         */
        mActivityRecognitionPendingIntent =
                PendingIntent.getService(mContext, 0, intent,
                        PendingIntent.FLAG_UPDATE_CURRENT);

    }

    /**
     * Turn off activity recognition updates
     *
     */
    public void stopActivityRecognitionUpdates(boolean onStop) {

        Log.d("SMSAutoResponder", "stopActivityRecognitionUpdates" );

        // Set the request type to STOP
        mRequestType = REQUEST_TYPE.STOP;

        /*
         * Test for Google Play services after setting the request type.
         * If Google Play services isn't present, the request can be
         * restarted. Don't check Google Service if we're in onStop() though.
         */
        if (!onStop){
            if(!servicesConnected()) {
                return;
            }
        }

        // If a request is not already underway
        if (!mInProgress) {
            if(mActivityRecognitionClient != null) {
                // Indicate that a request is in progress
                mInProgress = true;
                // Request a connection to Location Services
                mActivityRecognitionClient.connect();
            }
        } else {
            /*
             * A request is already underway. You can handle
             * this situation by disconnecting the client,
             * re-setting the flag, and then re-trying the
             * request.
             */
        }
    }

    /**
     * Update the visibility of the response list and title
     * based on the preference setting
     */
    private void showResponsesList(){
        Log.d("SMSAutoResponder", "showResponsesList" );

        // Get the views we want to hide/show
        TextView responseTitle = (TextView) findViewById(R.id.ListViewTitle);
        ListView responseList = (ListView) findViewById(R.id.listView);
        LinearLayout responseLayout = (LinearLayout) findViewById(R.id.listViewContainer);

        // figure out the visibility option
        int visibility = mshow_responses_on_main_activity ? View.VISIBLE : View.INVISIBLE;

        // hide/show the list and title
        responseTitle.setVisibility(visibility);
        responseList.setVisibility(visibility);
        responseLayout.setVisibility(visibility);

    }

    /**
     * Clear all the Activity History on App exit
     * if the preference has been set to do so.
     */
    private void clearActivityData(){
        // Get a handle to our database, so we can store/retrieve
        // recent activities.
        ActivityRecognitionSQLiteHelper dbActivities = new ActivityRecognitionSQLiteHelper(this);
        // clear the database
        dbActivities.deleteAllActivities();
    }
}
