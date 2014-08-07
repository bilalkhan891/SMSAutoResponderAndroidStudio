package com.ridgway.smsautoresponder;

/**
 * Created by ridgway on 7/30/14.
 */

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;

/**
 * Service that receives ActivityRecognition updates. It receives
 * updates in the background, even if the main Activity is not visible.
 */
public class ActivityRecognitionIntentService extends IntentService {

    public ActivityRecognitionIntentService(){
        super("ActivityRecognitionIntentService");
    }

    /**
     * Called when a new activity detection update is available.
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        // If the incoming intent contains an update
        if (ActivityRecognitionResult.hasResult(intent)) {
            // Get the update
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);

            // Get the most probable activity
            DetectedActivity mostProbableActivity = result.getMostProbableActivity();

            /*
             * Get the probability that this activity is the
             * the user's actual activity
             */
            int confidence = mostProbableActivity.getConfidence();

            /*
             * Get an integer describing the type of activity
             */
            int activityType = mostProbableActivity.getType();
            String activityName = getNameFromType(activityType);

            /*
             * At this point, you have retrieved all the information
             * for the current update. We can display this
             * information to the user in a notification, or
             * send it to an Activity or Service in a broadcast
             * Intent.
             */

            // add a new activity entry to the database,
            // so we can track the types of activities we're
            // recognizing to determine how well this Google Services
            // feature actually works.
            ActivityRecognitionSQLiteHelper dbActivities = new ActivityRecognitionSQLiteHelper(this);
            dbActivities.addActivity(activityName);

            Log.d("ActivityRecognitionIntentService", "onHandleIntent: detected_activity: " + activityName);

            // Broadcast the Activity back to the main activity
            //--- send a broadcast intent to update the detected location aware activity in the main activity ---
            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction("ACTIVITY_RECOGNITION_UPDATE_ACTION");
            broadcastIntent.putExtra("detected_activity", activityName);

            getApplicationContext().sendBroadcast(broadcastIntent);

        } else {
            /*
             * This implementation ignores intents that don't contain
             * an activity update.
             */
        }
    }

    /**
     * Map detected activity types to strings
     *@param activityType The detected activity type
     *@return A user-readable name for the type
     */
    private String getNameFromType(int activityType) {
        switch(activityType) {
            case DetectedActivity.IN_VEHICLE:
                return getString(R.string.activity_driving);
            case DetectedActivity.ON_BICYCLE:
                return getString(R.string.activity_cycling);
            case DetectedActivity.RUNNING:
                return getString(R.string.activity_running);
            case DetectedActivity.WALKING:
            case DetectedActivity.ON_FOOT:
                return getString(R.string.activity_hiking);
            case DetectedActivity.STILL:
                return getString(R.string.activity_stationary);
            case DetectedActivity.TILTING:
                return getString(R.string.activity_tilting);
            case DetectedActivity.UNKNOWN:
                return getString(R.string.activity_unknown);
        }
        return getString(R.string.activity_unknown);
    }

}