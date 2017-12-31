package com.aware.plugin.contacts_list;

import android.app.Service;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.os.IBinder;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Encrypter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by serrislew on 12/31/16.
 * Edited by Denzil 11.1.2017:
 * - refactored code to do less cycles
 * - account for multiple phone numbers, emails and group memberships
 * - each sync round has the same timestamp on the database (sync_date)
 * - added contact hashes that match with phone numbers used in calls and messages logs
 */

public class Contacts_Service extends Service {

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        long sync_date = System.currentTimeMillis();

        Cursor contacts = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (contacts != null && contacts.moveToFirst()) {
            do {
                String contact_id = contacts.getString(contacts.getColumnIndex(ContactsContract.Contacts._ID));
                String contact_name = contacts.getString(contacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                if (contact_name == null || contact_name.length() == 0) continue;

                JSONArray phone_numbers = new JSONArray();
                if (contacts.getInt(contacts.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)) != 0) {
                    Cursor phone = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + contact_id, null, null);
                    if (phone != null && phone.moveToFirst()) {
                        do {
                            try {
                                JSONObject phoneRow = new JSONObject();
                                phoneRow.put("type", phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)));
                                phoneRow.put("number", phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)));
                                phoneRow.put("hash", Encrypter.hashPhone(this, phoneRow.getString("number")));
                                phone_numbers.put(phoneRow);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        } while (phone.moveToNext());
                    }
                    if (phone != null && !phone.isClosed()) phone.close();
                }

                JSONArray emails = new JSONArray();
                Cursor email = getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null, ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=" + contact_id, null, null);
                if (email != null && email.moveToFirst()) {
                    do {
                        try {
                            JSONObject emailRow = new JSONObject();
                            emailRow.put("type", email.getString(email.getColumnIndex(ContactsContract.CommonDataKinds.Email.TYPE)));
                            emailRow.put("email", email.getString(email.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)));
                            emails.put(emailRow);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } while (email.moveToNext());
                }
                if (email != null && !email.isClosed()) email.close();

                JSONArray groups = new JSONArray();
                Cursor group = getContentResolver().query(
                        ContactsContract.Data.CONTENT_URI, null,
                        ContactsContract.Data.CONTACT_ID + "=" + contact_id + " AND " + ContactsContract.Data.MIMETYPE + "='" + ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE + "'",
                        null, null);
                if (group != null && group.moveToFirst()) {
                    do {
                        try {
                            JSONObject groupRow = new JSONObject();
                            groupRow.put("group", group.getString(group.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)));
                            groups.put(groupRow);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    } while (group.moveToNext());
                }
                if (group != null && !group.isClosed()) group.close();

                ContentValues contactInfo = new ContentValues();
                contactInfo.put(Provider.Contacts_Data.TIMESTAMP, System.currentTimeMillis());
                contactInfo.put(Provider.Contacts_Data.DEVICE_ID, Aware.getSetting(this, Aware_Preferences.DEVICE_ID));
                contactInfo.put(Provider.Contacts_Data.NAME, contact_name);
                contactInfo.put(Provider.Contacts_Data.PHONE_NUMBERS, phone_numbers.toString());
                contactInfo.put(Provider.Contacts_Data.EMAILS, emails.toString());
                contactInfo.put(Provider.Contacts_Data.GROUPS, groups.toString());
                contactInfo.put(Provider.Contacts_Data.SYNC_DATE, sync_date);

                try {
                    getContentResolver().insert(Provider.Contacts_Data.CONTENT_URI, contactInfo);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                }

                if (Aware.DEBUG)
                    Log.d(Aware.TAG, "Contact stored: " + contactInfo.toString());

            } while (contacts.moveToNext());
        }
        if (contacts != null && !contacts.isClosed()) contacts.close();

        stopSelf();

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
