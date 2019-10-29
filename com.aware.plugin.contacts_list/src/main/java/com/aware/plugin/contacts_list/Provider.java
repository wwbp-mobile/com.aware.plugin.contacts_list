package com.aware.plugin.contacts_list;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

/**
 * Created by denzil on 07/04/16.
 */
public class Provider extends ContentProvider {

    public static String AUTHORITY = "com.aware.plugin.contacts_list.provider.contacts_list"; //change to package.provider.your_plugin_name

    public static final int DATABASE_VERSION = 10; //increase this if you make changes to the database structure, i.e., rename columns, etc.

    public static final String DATABASE_NAME = "plugin_contacts_list.db"; //the database filename, use plugin_xxx for plugins.

    //For each table, add two indexes: DIR and ITEM. The index needs to always increment. Next one is 3, and so on.
    private static final int CONTACTS_DIR = 1;
    private static final int CONTACTS_ITEM = 2;

    //Put tables names in this array so AWARE knows what you have on the database
    public static final String[] DATABASE_TABLES = {
            "plugin_contacts_list"
    };

    /**
     * Create one of these per database table
     * In this example, we are adding example columns
     */
    public static final class Contacts_Data implements BaseColumns {

        private static String packageName;

        private Contacts_Data() {
        }

//        public static void setPackage(String root) {
//            packageName = root + ".provider.contacts_list";
//        }
//
//        public static String getPackage() {
//            if (packageName.length() == 0)
//            return packageName;
//        }

        public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/plugin_contacts_list");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.com.aware.plugin.contacts_list"; //modify me
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.com.aware.plugin.contacts_list"; //modify me

        public static String _ID = "_id";
        public static String TIMESTAMP = "timestamp";
        public static String DEVICE_ID = "device_id";
        public static String NAME = "name";
        public static String PHONE_NUMBERS = "phone_numbers";
        public static String EMAILS = "emails";
        public static String GROUPS = "groups";
        public static String SYNC_DATE = "sync_date";
    }

    /**
     * Share the fields with AWARE so we can replicate the table schema on the server
     */
    public static final String[] TABLES_FIELDS = {
            Contacts_Data._ID + " integer primary key autoincrement," +
                    Contacts_Data.TIMESTAMP + " real default 0," +
                    Contacts_Data.DEVICE_ID + " text default ''," +
                    Contacts_Data.NAME + " text default ''," +
                    Contacts_Data.PHONE_NUMBERS + " text default ''," +
                    Contacts_Data.EMAILS + " text default ''," +
                    Contacts_Data.GROUPS + " text default ''," +
                    Contacts_Data.SYNC_DATE + " real default 0"
    };

    //Helper variables for ContentProvider - don't change me
    private static UriMatcher sUriMatcher;
    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;

    private void initialiseDatabase() {
        if (dbHelper == null)
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (database == null)
            database = dbHelper.getWritableDatabase();
    }

    //For each table, create a hashmap needed for database queries
    private static HashMap<String, String> contactsHash = null;

    /**
     * Returns the provider authority that is dynamic
     *
     * @return
     */
    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.contacts_list";
        return AUTHORITY;
    }

    @Override
    public boolean onCreate() {
        //This is a hack to allow providers to be reusable in any application/plugin by making the authority dynamic using the package name of the parent app

        AUTHORITY = getContext().getPackageName() + ".provider.contacts_list";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0], CONTACTS_DIR);
        sUriMatcher.addURI(AUTHORITY, DATABASE_TABLES[0] + "/#", CONTACTS_ITEM);

        //Create each table hashmap so Android knows how to insert data to the database. Put ALL table fields.
        contactsHash = new HashMap<>();
        contactsHash.put(Contacts_Data._ID, Contacts_Data._ID);
        contactsHash.put(Contacts_Data.TIMESTAMP, Contacts_Data.TIMESTAMP);
        contactsHash.put(Contacts_Data.DEVICE_ID, Contacts_Data.DEVICE_ID);
        contactsHash.put(Contacts_Data.NAME, Contacts_Data.NAME);
        contactsHash.put(Contacts_Data.PHONE_NUMBERS, Contacts_Data.PHONE_NUMBERS);
        contactsHash.put(Contacts_Data.EMAILS, Contacts_Data.EMAILS);
        contactsHash.put(Contacts_Data.GROUPS, Contacts_Data.GROUPS);
        contactsHash.put(Contacts_Data.SYNC_DATE, Contacts_Data.SYNC_DATE);

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        initialiseDatabase();

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        switch (sUriMatcher.match(uri)) {

            //Add all tables' DIR entries, with the right table index
            case CONTACTS_DIR:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(contactsHash); //the hashmap of the table
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        //Don't change me
        try {
            Cursor c = qb.query(database, projection, selection, selectionArgs,
                    null, null, sortOrder);
            c.setNotificationUri(getContext().getContentResolver(), uri);
            return c;
        } catch (IllegalStateException e) {
            if (Aware.DEBUG)
                Log.e(Aware.TAG, e.getMessage());
            return null;
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            //Add each table indexes DIR and ITEM
            case CONTACTS_DIR:
                return Contacts_Data.CONTENT_TYPE;
            case CONTACTS_ITEM:
                return Contacts_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues new_values) {

        initialiseDatabase();

        ContentValues values = (new_values != null) ? new ContentValues(new_values) : new ContentValues();

        database.beginTransaction();

        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case
            case CONTACTS_DIR:
                long _id = database.insertWithOnConflict(DATABASE_TABLES[0], Contacts_Data.DEVICE_ID, values, SQLiteDatabase.CONFLICT_IGNORE);
                database.setTransactionSuccessful();
                database.endTransaction();
                if (_id > 0) {
                    Uri dataUri = ContentUris.withAppendedId(Contacts_Data.CONTENT_URI, _id);
                    getContext().getContentResolver().notifyChange(dataUri, null, false);
                    return dataUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {
            case CONTACTS_DIR:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        initialiseDatabase();

        database.beginTransaction();

        int count;
        switch (sUriMatcher.match(uri)) {

            //Add each table DIR case
            case CONTACTS_DIR:
                count = database.update(DATABASE_TABLES[0], values, selection, selectionArgs);
                break;

            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }

        database.setTransactionSuccessful();
        database.endTransaction();

        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }
}
