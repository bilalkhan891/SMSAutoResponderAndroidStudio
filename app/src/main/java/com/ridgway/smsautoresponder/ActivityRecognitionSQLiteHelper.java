package com.ridgway.smsautoresponder;

/**
 * Created by ridgway on 7/27/14.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.sql.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class ActivityRecognitionSQLiteHelper extends SQLiteOpenHelper {

    public static final String TABLE_ACTIVITY = "activity";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_ACTIVITY = "activity";
    private static final String[] COLUMNS = {COLUMN_ID, COLUMN_DATE, COLUMN_ACTIVITY};

    private static final String DATABASE_NAME = "activity.db";
    private static final int DATABASE_VERSION = 1;

    // Database creation sql statement
    private static final String DATABASE_CREATE = "create table "
            + TABLE_ACTIVITY + "("
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_DATE + " timestamp not null default current_timestamp, "
            + COLUMN_ACTIVITY + " text not null);";


    private static final String QUERY_ALL_DESC = "SELECT "
            + COLUMN_ID + ", (datetime(" + COLUMN_DATE + ", 'localtime')) AS "
            + COLUMN_DATE + ", "
            + COLUMN_ACTIVITY + " FROM " + TABLE_ACTIVITY
            + " ORDER BY " + COLUMN_ID + " DESC";


    public ActivityRecognitionSQLiteHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        Log.w(SMSSQLiteHelper.class.getName(), "Create the SQLite Database to track responses");
        database.execSQL(DATABASE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(SMSSQLiteHelper.class.getName(),
                "Upgrading database from version " + oldVersion + " to "
                        + newVersion + ", which will destroy all old data");
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACTIVITY);
        onCreate(db);
    }

    // Add an activity to the database
    public void addActivity(String activity){
        Log.d("ActivityRecognitionSQLiteHelper: addActivity: ", activity);
        // 1. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();

        // 2. create ContentValues to add key "column"/value
        ContentValues values = new ContentValues();

        values.put(COLUMN_ACTIVITY, activity); // write number

        // 3. insert the new number. Date/Time will be added
        // automatically by SQLite, due to the default option on the column.
        db.insert(TABLE_ACTIVITY, // table
                null, //nullColumnHack
                values); // key/value -> keys = column names/ values = column values

        // 4. close
        db.close();
    }

    // We want to be able to wipe out the contents of the database
    public void deleteAllActivities(){
        List<Integer> responses = getAllActivities();
        ListIterator<Integer> listIterator = responses.listIterator();
        while (listIterator.hasNext()) {
            deleteActivity(listIterator.next());
        }

    }

    // Get all the stored activities
    public List<Integer> getAllActivities() {
        List<Integer> responses = new LinkedList<Integer>();

        // get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(QUERY_ALL_DESC, null);

        // go over each row, grab the id column value and add it to list
        int id;
        if (cursor.moveToFirst()) {
            do {
                id = Integer.parseInt(cursor.getString(0));
                Log.d("ActivityRecognitionSQLiteHelper: getAllActivities()", ""+id);

                // Add response id to list
                responses.add(id);
            } while (cursor.moveToNext());
        }

        // return books
        return responses;
    }

    // Get all the data return the appropriate Cursor.
    public Cursor getAllData(){

        Log.d("ActivityRecognitionSQLiteHelper", "getAllData SQL: " + QUERY_ALL_DESC);

        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery(QUERY_ALL_DESC, null);
    }

    // Delete a stored activity by database id value.
    public void deleteActivity(Integer id) {

        // 1. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();

        // 2. delete
        db.delete(TABLE_ACTIVITY, //table name
                COLUMN_ID + " = ?",  // selections
                new String[] { String.valueOf(id) }); //selections args

        // 3. close
        db.close();

        //log
        Log.d("ActivityRecognitionSQLiteHelper: deleteActivity", ""+id);

    }

}