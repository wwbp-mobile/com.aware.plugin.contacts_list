package com.aware.plugin.contacts_list;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Encrypter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * Created by serrislew on 12/31/16.
 * Edited by Denzil 11.1.2017:
 * - refactored code to do less cycles
 * - account for multiple phone numbers, emails and group memberships
 * - each sync round has the same timestamp on the database (sync_date)
 * - added contact hashes that match with phone numbers used in calls and messages logs
 */

public class Contacts_Service extends IntentService {

    public Contacts_Service() {
        super(Plugin.TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        long sync_date = System.currentTimeMillis();

        Cursor contacts = getContentResolver().query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null);
        if (contacts != null && contacts.moveToFirst()) {
            do {
                String contact_id = contacts.getString(contacts.getColumnIndex(ContactsContract.Contacts._ID));
                String contact_name = contacts.getString(contacts.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

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
                        ContactsContract.Data.CONTACT_ID + "=" + contact_id + " AND " + ContactsContract.Data.MIMETYPE + "=" + ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
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
                if (group != null && ! group.isClosed()) group.close();

                ContentValues contactInfo = new ContentValues();
                contactInfo.put(Provider.Contacts_Data.TIMESTAMP, System.currentTimeMillis());
                contactInfo.put(Provider.Contacts_Data.DEVICE_ID, Aware.getSetting(this, Aware_Preferences.DEVICE_ID));
                contactInfo.put(Provider.Contacts_Data.NAME, contact_name);
                contactInfo.put(Provider.Contacts_Data.PHONE_NUMBERS, phone_numbers.toString());
                contactInfo.put(Provider.Contacts_Data.EMAILS, emails.toString());
                contactInfo.put(Provider.Contacts_Data.GROUPS, groups.toString());
                contactInfo.put(Provider.Contacts_Data.SYNC_DATE, sync_date);

                getContentResolver().insert(Provider.Contacts_Data.CONTENT_URI, contactInfo);

                if (Plugin.DEBUG)
                    Log.d(Plugin.TAG, "Contact stored: " + contactInfo.toString());

            } while (contacts.moveToNext());
        }
        if (contacts != null && !contacts.isClosed()) contacts.close();

//        if (intent != null) {
//            ContentResolver cr = getContentResolver();
//            Cursor cur = cr.query(ContactsContract.Contacts.CONTENT_URI,
//                    null, null, null, null);
//            //contacts ="";
//            if (cur.getCount() > 0) {
//                while (cur.moveToNext()) {
//                    String id = cur.getString(cur.getColumnIndex(ContactsContract.Contacts._ID));
//                    String name = cur.getString(cur.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));
//                    if (Integer.parseInt(cur.getString(cur.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER))) > 0) {
//                        System.out.println("name : " + name + ", ID : " + id + "\n");
//                        //contacts += ("name : " + name + ", ID : " + id + "\n");
//
//                        // get the phone number
//                        Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null,
//                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
//                                new String[]{id}, null);
//                        while (pCur.moveToNext()) {
//                            String phone = pCur.getString(
//                                    pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER));
//                            String phoneType = pCur.getString(
//                                    pCur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE));
//                            //creates new row for each phone number, not for each contact name
//                            ContentValues data = new ContentValues();
//                            data.put(Provider.Contacts_Data.TIMESTAMP, System.currentTimeMillis());
//                            //data.put(Provider.Contacts_Data.TIMESTAMP, phone); //prevent duplicate syncing, use phone number instead of timestamp to distinguish entry
//                            data.put(Provider.Contacts_Data.DEVICE_ID, Aware.getSetting(getApplicationContext(), Aware_Preferences.DEVICE_ID));
//                            data.put(Provider.Contacts_Data.NAME, name);
//                            data.put(Provider.Contacts_Data.PHONE_NUMBER, phone);
//                            data.put(Provider.Contacts_Data.PHONE_TYPE, phoneType);
//                            System.out.println("phone" + phone + " phone type: " + phoneType + "\n");
//                            //contacts += ("phone" + phone + " phone type: " + phoneType + "\n");
//
//                            // get email and type
//                            Cursor emailCur = cr.query(
//                                    ContactsContract.CommonDataKinds.Email.CONTENT_URI,
//                                    null,
//                                    ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
//                                    new String[]{id}, null);
//                            String email_list = "";
//                            while (emailCur.moveToNext()) {
//                                // This would allow you get several email addresses
//                                // if the email addresses were stored in an array
//                                String email = emailCur.getString(
//                                        emailCur.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA));
//                                email_list += email;
//                                if (!emailCur.isLast()) {
//                                    email_list += "\n";
//                                }
//                                System.out.println("Email " + email + "\n");
//                                //contacts += ("Email " + email + "\n");
//                            }
//                            data.put(Provider.Contacts_Data.EMAIL, email_list);
//                            emailCur.close();
//
//                            // Get group, not putting into database right now
//                            String groupWhere = ContactsContract.Data.CONTACT_ID + " = ? AND " + ContactsContract.Data.MIMETYPE + " = ?";
//                            String[] groupWhereParams = new String[]{id,
//                                    ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE};
//                            Cursor groupCur = cr.query(ContactsContract.Data.CONTENT_URI, null, groupWhere, groupWhereParams, null);
//                            if (groupCur.moveToFirst()) {
//                                String group = groupCur.getString(groupCur.getColumnIndex(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID));
//                                //data.put(Provider.Contacts_Data.GROUP, group);
//                                System.out.println("GROUP " + group + "\n");
//                                //contacts += ("GROUP " + group + "\n");
//                            }
//                            groupCur.close();
//
//                            getContentResolver().insert(Provider.Contacts_Data.CONTENT_URI, data);
//                        }
//                        pCur.close();
//                        System.out.println("in plugin_contacts.db, table 'contacts' at " + System.currentTimeMillis());
//
//                    }
//                }
//            }
//        }
    }
}
