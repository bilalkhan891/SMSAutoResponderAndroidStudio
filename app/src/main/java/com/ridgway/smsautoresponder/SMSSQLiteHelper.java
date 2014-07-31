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

public class SMSSQLiteHelper extends SQLiteOpenHelper {

    public static final String TABLE_SMS = "smsresponses";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_DATE = "date";
    public static final String COLUMN_NUMBER = "phone_number";
    private static final String[] COLUMNS = {COLUMN_ID, COLUMN_DATE, COLUMN_NUMBER};

    private static final String DATABASE_NAME = "sms.db";
    private static final int DATABASE_VERSION = 1;

    // Database creation sql statement
    private static final String DATABASE_CREATE = "create table "
            + TABLE_SMS + "("
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_DATE + " timestamp not null default current_timestamp, "
            + COLUMN_NUMBER + " text not null);";


    private static final String QUERY_ALL_DESC = "SELECT "
            + COLUMN_ID + ", (datetime(" + COLUMN_DATE + ", 'localtime')) AS "
            + COLUMN_DATE + ", "
            + COLUMN_NUMBER + " FROM " + TABLE_SMS
            + " ORDER BY " + COLUMN_ID + " DESC";


    public SMSSQLiteHelper(Context context) {
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
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SMS);
        onCreate(db);
    }

    // Add a response to the database
    public void addResponse(String number){
        Log.d("SMSSQLiteHelper: addResponse: ", number);
        // 1. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();

        // 2. create ContentValues to add key "column"/value
        ContentValues values = new ContentValues();

        values.put(COLUMN_NUMBER, number); // write number

        // 3. insert the new number. Date/Time will be added
        // automatically by SQLite, due to the default option on the column.
        db.insert(TABLE_SMS, // table
                null, //nullColumnHack
                values); // key/value -> keys = column names/ values = column values

        // 4. close
        db.close();
    }

    // We want to be able to wipe out the contents of the database
    public void deleteAllResponses(){
        List<Integer> responses = getAllResponses();
        ListIterator<Integer> listIterator = responses.listIterator();
        while (listIterator.hasNext()) {
            deleteResponse(listIterator.next());
        }

    }

    // Get all the stored responses
    public List<Integer> getAllResponses() {
        List<Integer> responses = new LinkedList<Integer>();

        // get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(QUERY_ALL_DESC, null);

        // go over each row, grab the id column value and add it to list
        int id;
        if (cursor.moveToFirst()) {
            do {
                id = Integer.parseInt(cursor.getString(0));
                Log.d("SMSSQLiteHelper: getAllResponses()", ""+id);

                // Add response id to list
                responses.add(id);
            } while (cursor.moveToNext());
        }

        // return books
        return responses;
    }

    // Get all the data return the appropriate Cursor.
    public Cursor getAllData(){

        Log.d("SMSSQLiteHelper", "getAllData SQL: " + QUERY_ALL_DESC);

        SQLiteDatabase db = this.getWritableDatabase();
        return db.rawQuery(QUERY_ALL_DESC, null);
    }

    // Delete a stored response by database id value.
    public void deleteResponse(Integer id) {

        // 1. get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();

        // 2. delete
        db.delete(TABLE_SMS, //table name
                COLUMN_ID + " = ?",  // selections
                new String[] { String.valueOf(id) }); //selections args

        // 3. close
        db.close();

        //log
        Log.d("SMSSQLiteHelper: deleteResponse", ""+id);

    }

    // get the timestamp for the most recent response sent to a number
    public long getLatestResponseTime(String number){
        long responseDateMillis = 0;

        String QUERY_LATEST_RESPONSE_BY_NUMBER = "SELECT _id, (strftime('%s', date) * 1000) AS millis FROM " + TABLE_SMS
                + " WHERE " + COLUMN_NUMBER + " = " + number
                + " ORDER BY " + COLUMN_ID + " DESC";

        Log.d("SMSSQLiteHelper: getLatestResponseTime", "Query String: " + QUERY_LATEST_RESPONSE_BY_NUMBER);

        // get reference to writable DB
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor cursor = db.rawQuery(QUERY_LATEST_RESPONSE_BY_NUMBER, null);
        if(cursor == null || cursor.getCount() == 0){
            Log.d("SMSSQLiteHelper: getLatestResponseTime", "No previous response sent to: " + number);
        }
        else {
            // Move to the first returned response, which
            // is the most recent one in the database.
            cursor.moveToFirst();
            responseDateMillis = cursor.getLong(cursor.getColumnIndexOrThrow("millis"));
            Log.d("SMSSQLiteHelper: getLatestResponseTime", "Found a previous response to: " + number);
        }

        return responseDateMillis;

    }
}