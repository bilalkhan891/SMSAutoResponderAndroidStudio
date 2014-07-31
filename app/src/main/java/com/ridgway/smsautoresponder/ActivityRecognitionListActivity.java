package com.ridgway.smsautoresponder;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.ListView;

import com.ridgway.smsautoresponder.R;

public class ActivityRecognitionListActivity extends ActionBarActivity {

    private ActivityRecognitionSQLiteHelper dbActivities; // Database link for storing activity information
    private ListView listView; // Main activity ListView to display recent detected activities
    private ActivityRecognitionCursorAdapter activityAdapter; // Adapter between the database and ListView

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_recognition_list);

        // Get a handle to our database, so we can store/retrieve
        // recent activities.
        dbActivities = new ActivityRecognitionSQLiteHelper(this);

        // Database query can be a time consuming task ..
        // so its safe to call database query in another thread
        // Handler, will handle this stuff.
        // Start this early, so we can get everything else
        // up and running while this goes on. Limits the perceived delay.
        listView = (ListView) findViewById(R.id.listView);
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                activityAdapter = new ActivityRecognitionCursorAdapter(ActivityRecognitionListActivity.this, dbActivities.getAllData());
                listView.setAdapter(activityAdapter);
            }
        });

        // Add a header row to the listview
        LayoutInflater inflater = getLayoutInflater();
        ViewGroup header = (ViewGroup) inflater.inflate(R.layout.activity_listview_header, listView, false);
        listView.addHeaderView(header, null, false);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.activity_recognition_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_clear_data) {
            clearActivityData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    /**
     * Update the database to remove all the activity history
     * and update the listview to reflect the new data
     */
    private void clearActivityData(){
        new AlertDialog.Builder(this)
                .setTitle(R.string.acknowledge_delete_activities_title )
                .setMessage(R.string.acknowledge_delete_activities_msg)
                .setPositiveButton(R.string.dlg_yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        //do stuff onclick of YES
                        // clear the database
                        dbActivities.deleteAllActivities();
                        // update the listView
                        activityAdapter.changeCursor(dbActivities.getAllData());
                    }
                })
                .setNegativeButton(R.string.dlg_cancel, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        //do nothing onclick of CANCEL
                    }
                }).show();

    }
}
