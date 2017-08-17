package com.aware.plugin.contacts_list;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import com.aware.Aware;
import com.aware.Aware_Preferences;
import com.aware.utils.Aware_Plugin;
import com.aware.utils.Scheduler;

import org.json.JSONException;

public class Plugin extends Aware_Plugin {

    public static final String SCHEDULER_PLUGIN_CONTACTS = "SCHEDULER_PLUGIN_CONTACTS";

    @Override
    public void onCreate() {
        super.onCreate();

        TAG = "AWARE::"+getResources().getString(R.string.app_name);

        //Any active plugin/sensor shares its overall context using broadcasts
        CONTEXT_PRODUCER = new ContextProducer() {
            @Override
            public void onContext() {
                //Broadcast your context here
            }
        };

        //Add permissions you need (Support for Android M). By default, AWARE asks access to the #Manifest.permission.WRITE_EXTERNAL_STORAGE
        REQUIRED_PERMISSIONS.add(Manifest.permission.READ_CONTACTS);

        //To sync data to the server, you'll need to set this variables from your ContentProvider
        DATABASE_TABLES = Provider.DATABASE_TABLES;
        TABLES_FIELDS = Provider.TABLES_FIELDS;
        CONTEXT_URIS = new Uri[]{ Provider.Contacts_Data.CONTENT_URI }; //this syncs Contacts_Data to server
    }

    //This function gets called every 5 minutes by AWARE to make sure this plugin is still running.
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);

        if (PERMISSIONS_OK) {

            //Check if the user has toggled the debug messages
            DEBUG = Aware.getSetting(this, Aware_Preferences.DEBUG_FLAG).equals("true");

            //Initialize our plugin's settings
            Aware.setSetting(this, Settings.STATUS_PLUGIN_CONTACTS, true);

            if (Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS).length() == 0) {
                Aware.setSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS, 1);//set to one day
            }

            try{
                Scheduler.Schedule contacts_sync = Scheduler.getSchedule(this, SCHEDULER_PLUGIN_CONTACTS);
                if (contacts_sync==null || contacts_sync.getInterval() != Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS)))
                {
                    contacts_sync = new Scheduler.Schedule(SCHEDULER_PLUGIN_CONTACTS);
                    contacts_sync.setInterval(Long.parseLong(Aware.getSetting(this, Settings.FREQUENCY_PLUGIN_CONTACTS))*60*24);//*60 mins/hrs * 24 hrs/day
                    contacts_sync.setActionType(Scheduler.ACTION_TYPE_SERVICE);
                    contacts_sync.setActionClass(getPackageName() + "/" + Contacts_Service.class.getName());
                    Scheduler.saveSchedule(this, contacts_sync);
                }
            }
            catch(JSONException e) {
                e.printStackTrace();
            }

            if (!Aware.isSyncEnabled(this, Provider.getAuthority(this)) && Aware.isStudy(this) && getApplicationContext().getPackageName().equalsIgnoreCase("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone)) {
                ContentResolver.setIsSyncable(Aware.getAWAREAccount(this), Provider.getAuthority(this), 1);
                ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Provider.getAuthority(this), true);
                ContentResolver.addPeriodicSync(
                        Aware.getAWAREAccount(this),
                        Provider.getAuthority(this),
                        Bundle.EMPTY,
                        Long.parseLong(Aware.getSetting(this, Aware_Preferences.FREQUENCY_WEBSERVICE)) * 60
                );
            }

            Aware.startAWARE(this);
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (Aware.isStudy(this) && (getApplicationContext().getPackageName().equalsIgnoreCase("com.aware.phone") || getApplicationContext().getResources().getBoolean(R.bool.standalone))) {
            ContentResolver.setSyncAutomatically(Aware.getAWAREAccount(this), Provider.getAuthority(this), false);
            ContentResolver.removePeriodicSync(
                    Aware.getAWAREAccount(this),
                    Provider.getAuthority(this),
                    Bundle.EMPTY
            );
        }

        Scheduler.removeSchedule(this, SCHEDULER_PLUGIN_CONTACTS);
        Aware.setSetting(this, Settings.STATUS_PLUGIN_CONTACTS, false);

        Aware.stopAWARE(this);
    }
}




