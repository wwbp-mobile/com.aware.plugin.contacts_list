package com.aware.plugin.contacts_list;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncRequest;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.Encrypter;
import com.aware.utils.Scheduler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class Plugin extends Aware_Plugin {
    public static final String SCHEDULER_PLUGIN_CONTACTS = "SCHEDULER_PLUGIN_CONTACTS";
    public static final String ACTION_REFRESH_CONTACTS = "ACTION_REFRESH_CONTACTS";

    @Override
    public void onCreate() {
        super.onCreate();

        AUTHORITY = Provider.getAuthority(this);
        TAG = "AWARE::Contacts List";
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_CONTACTS);
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            if (intent != null && intent.getAction() != null && intent.getAction().equalsIgnoreCase(ACTION_REFRESH_CONTACTS)) {

                AUTHORITY = Provider.getAuthority(getApplicationContext());

                long sync_date = System.currentTimeMillis();

                Cursor contacts = getApplicationContext().getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
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
                                        phoneRow.put("hash", Encrypter.hashPhone(getApplicationContext(), phoneRow.getString("number")));
                                        phone_numbers.put(phoneRow);
                                    } catch (JSONException e) {
                                        e.printStackTrace();
                                    }
                                } while (phone.moveToNext());
                            }
                            if (phone != null && !phone.isClosed()) phone.close();
                        }

                        JSONArray emails = new JSONArray();
                        Cursor email = getApplicationContext().getContentResolver().query(ContactsContract.CommonDataKinds.Email.CONTENT_URI, null, ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=" + contact_id, null, null);
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
                        Cursor group = getApplicationContext().getContentResolver().query(
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
                        contactInfo.put(Provider.Contacts_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
                        contactInfo.put(Provider.Contacts_Data.NAME, contact_name);
                        contactInfo.put(Provider.Contacts_Data.PHONE_NUMBERS, phone_numbers.toString());
                        contactInfo.put(Provider.Contacts_Data.EMAILS, emails.toString());
                        contactInfo.put(Provider.Contacts_Data.GROUPS, groups.toString());
                        contactInfo.put(Provider.Contacts_Data.SYNC_DATE, sync_date);

                        getApplicationContext().getContentResolver().insert(Provider.Contacts_Data.CONTENT_URI, contactInfo);

                    } while (contacts.moveToNext());
                }
                if (contacts != null && !contacts.isClosed()) contacts.close();

//                Intent contactsSync = new Intent(this, AsyncContacts.class);
//                startService(contactsSync);
//                return START_STICKY;
            }

            //Check if the user has toggled the debug messages
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            if (Aware.getSetting(getApplicationContext(), Settings.STATUS_PLUGIN_CONTACTS).length() == 0) {
                Aware.setSetting(getApplicationContext(), Settings.STATUS_PLUGIN_CONTACTS, true);
            } else {
                if (Aware.getSetting(getApplicationContext(), Settings.STATUS_PLUGIN_CONTACTS).equalsIgnoreCase("false")) {
                    Aware.stopPlugin(getApplicationContext(), getPackageName());
                    return START_STICKY;
                }
            }

            if (Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS).length() == 0) {
                Aware.setSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS, 1);//set to one day
            }

            try {
                Scheduler.Schedule contacts_sync = Scheduler.getSchedule(this, SCHEDULER_PLUGIN_CONTACTS);
                if (contacts_sync == null || contacts_sync.getInterval() != Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS))) {
                    contacts_sync = new Scheduler.Schedule(SCHEDULER_PLUGIN_CONTACTS);
                    contacts_sync.setInterval(Integer.parseInt(Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS)) * 60 * 24);
                    contacts_sync.setActionType(Scheduler.ACTION_TYPE_SERVICE);
                    contacts_sync.setActionIntentAction(ACTION_REFRESH_CONTACTS);
                    contacts_sync.setActionClass(getPackageName() + "/" + Plugin.class.getName());
                    Scheduler.saveSchedule(this, contacts_sync);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }

            if (Aware.isStudy(this)) {
                Account aware_account = Aware.getAWAREAccount(getApplicationContext());
                long frequency = Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60;

                ContentResolver.setIsSyncable(aware_account, Provider.getAuthority(getApplicationContext()), 1);
                ContentResolver.setSyncAutomatically(aware_account, Provider.getAuthority(getApplicationContext()), true);
                SyncRequest request = new SyncRequest.Builder()
                        .syncPeriodic(frequency, frequency / 3)
                        .setSyncAdapter(aware_account, Provider.getAuthority(getApplicationContext()))
                        .setExtras(new Bundle()).build();
                ContentResolver.requestSync(request);
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        Aware.setSetting(this, Settings.STATUS_PLUGIN_CONTACTS, false);
        Scheduler.removeSchedule(this, SCHEDULER_PLUGIN_CONTACTS);

        ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(getApplicationContext()), Provider.getAuthority(getApplicationContext()), false);
        ContentResolver.removePeriodicSync(
                Aware.getAWAREAccount(this),
                Provider.getAuthority(this),
                Bundle.EMPTY
        );
    }
}




