package com.ridgway.smsautoresponder;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

public class SettingsActivity extends ActionBarActivity {

	public static final String PREFS_NAME = "SMS_SharedPrefs";

	String strDrive = "";
	String strBike = "";
	String strRun = "";
	String strHike = "";
	String strDisturb = "";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		
		SharedPreferences sharedPref = this.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
		
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

		EditText editDrivingText = (EditText) findViewById(R.id.editDrivingText);
		editDrivingText.setText(strDrive);

		EditText editBikingText = (EditText) findViewById(R.id.editBikingText);
		editBikingText.setText(strBike);
		
		EditText editRunningText = (EditText) findViewById(R.id.editRunningText);
		editRunningText.setText(strRun);
		
		EditText editHikingText = (EditText) findViewById(R.id.editHikingText);
		editHikingText.setText(strHike);

		EditText editDisturbText = (EditText) findViewById(R.id.editDisturbText);
		editDisturbText.setText(strDisturb);


	}

	@Override
	protected void onPause(){
        super.onPause();
        savePrefs();
	}

	@Override
	protected void onStop(){
        super.onStop();
        savePrefs();
	}

	private void savePrefs(){

		EditText editDrivingText = (EditText) findViewById(R.id.editDrivingText);
		String strDrive = editDrivingText.getText().toString();

		EditText editBikingText = (EditText) findViewById(R.id.editBikingText);
		String strBike = editBikingText.getText().toString();
		
		EditText editRunningText = (EditText) findViewById(R.id.editRunningText);
		String strRun = editRunningText.getText().toString();
		
		EditText editHikingText = (EditText) findViewById(R.id.editHikingText);
		String strHike = editHikingText.getText().toString();

		EditText editDisturbText = (EditText) findViewById(R.id.editDisturbText);
		String strDisturb = editDisturbText.getText().toString();

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();


        editor.putString(getString(R.string.saved_response_driving), strDrive);
        editor.putString(getString(R.string.saved_response_biking), strBike);
        editor.putString(getString(R.string.saved_response_running), strRun);
        editor.putString(getString(R.string.saved_response_hiking), strHike);
        editor.putString(getString(R.string.saved_response_donotdisturb), strDisturb);

        
        editor.apply();
        editor.commit();        
		
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/** Called when the user clicks the Start button */
	public void resetDefaults(View view) {
	    // Do something in response to button
	
		String defaultDrive = getResources().getString(R.string.response_driving);
		String defaultBike = getResources().getString(R.string.response_cycling);
		String defaultRun = getResources().getString(R.string.response_running);
		String defaultHike = getResources().getString(R.string.response_hiking);
		String defaultDisturb = getResources().getString(R.string.response_donot_disturb);

		EditText editDrivingText = (EditText) findViewById(R.id.editDrivingText);
		editDrivingText.setText(defaultDrive);

		EditText editBikingText = (EditText) findViewById(R.id.editBikingText);
		editBikingText.setText(defaultBike);
		
		EditText editRunningText = (EditText) findViewById(R.id.editRunningText);
		editRunningText.setText(defaultRun);
		
		EditText editHikingText = (EditText) findViewById(R.id.editHikingText);
		editHikingText.setText(defaultHike);

		EditText editDisturbText = (EditText) findViewById(R.id.editDisturbText);
		editDisturbText.setText(defaultDisturb);

	}
	
	
}
