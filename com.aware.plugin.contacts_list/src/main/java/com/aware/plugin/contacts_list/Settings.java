package com.aware.plugin.contacts_list;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

import com.aware.Aware;
import com.aware.ui.AppCompatPreferenceActivity;

public class Settings extends AppCompatPreferenceActivity implements OnSharedPreferenceChangeListener {

    //Plugin settings in XML @xml/preferences_contacts_list
    public static final String STATUS_PLUGIN_CONTACTS = "status_plugin_contacts";

    //Frequency of Plugin
    public static final String FREQUENCY_PLUGIN_CONTACTS = "frequency_plugin_contacts";

    //Plugin settings UI elements
    private static CheckBoxPreference status;
    private static EditTextPreference frequency;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences_contacts_list);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        status = (CheckBoxPreference) findPreference(STATUS_PLUGIN_CONTACTS);
        if( Aware.getSetting(this, STATUS_PLUGIN_CONTACTS).length() == 0 ) {
            Aware.setSetting( this, STATUS_PLUGIN_CONTACTS, true ); //by default, the setting is true on install
        }
        status.setChecked(Aware.getSetting(getApplicationContext(), STATUS_PLUGIN_CONTACTS).equals("true"));

        frequency = (EditTextPreference) findPreference(FREQUENCY_PLUGIN_CONTACTS);
        if( Aware.getSetting(this, FREQUENCY_PLUGIN_CONTACTS).length() == 0 ) {
            Aware.setSetting( this, FREQUENCY_PLUGIN_CONTACTS, 1);//by default, the setting is 1 day
        }
        frequency.setSummary("Every " + Aware.getSetting(getApplicationContext(), FREQUENCY_PLUGIN_CONTACTS) + " day(s)");
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Preference setting = findPreference(key);
        if( setting.getKey().equals(STATUS_PLUGIN_CONTACTS) ) {
            Aware.setSetting(this, key, sharedPreferences.getBoolean(key, false));
            status.setChecked(sharedPreferences.getBoolean(key, false));
        }
        if (setting.getKey().equals(FREQUENCY_PLUGIN_CONTACTS)) {
            Aware.setSetting(getApplicationContext(), key, sharedPreferences.getString(key, "1"));
            frequency.setSummary("Every "+ Aware.getSetting(getApplicationContext(), FREQUENCY_PLUGIN_CONTACTS) + " day(s)");
        }
        if (Aware.getSetting(this, STATUS_PLUGIN_CONTACTS).equals("true")) {
            Aware.startPlugin(getApplicationContext(), "com.aware.plugin.contacts_list");
        } else {
            Aware.stopPlugin(getApplicationContext(), "com.aware.plugin.contacts_list");
        }
    }
}
