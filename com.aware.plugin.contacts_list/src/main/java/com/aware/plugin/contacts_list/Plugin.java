package com.aware.plugin.contacts_list;

import android.Manifest;
import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SyncRequest;
import android.database.Cursor;
import android.database.sqlite.SQLiteDiskIOException;
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

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
            }
        };

        //Add permissions you need (Support for Android M). By default, AWARE asks access to the #Manifest.permission.WRITE_EXTERNAL_STORAGE
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_CONTACTS);
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            if (intent != null && intent.getAction() != null && intent.getAction().equalsIgnoreCase(ACTION_REFRESH_CONTACTS)) {
                Intent contactsSync = new Intent(this, AsyncContacts.class);
                startService(contactsSync);
                return START_STICKY;
            }

            //Check if the user has toggled the debug messages
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_CONTACTS, true);

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




