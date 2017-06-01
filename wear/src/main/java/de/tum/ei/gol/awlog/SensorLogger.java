package de.tum.ei.gol.awlog;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Peter on 24.02.2016.
 */
public class SensorLogger {

    // Settings
    public final static String UNIQUENAME       = "uniquename";
    public final static String USEWAKELOCK      = "usewakelock";
    public final static String STARTONMOTION    = "startonmotion";
    public final static String LOGACCELEROMETER = "logaccelerometer";
    public final static String LOGGYROSCOPE     = "loggyroscope";
    public final static String LOGGRAVITY       = "loggravity";

    public static class BooleanSettings extends HashMap<String, Boolean> {
        BooleanSettings() {
            put(UNIQUENAME,         true);
            put(USEWAKELOCK,        true);
            put(STARTONMOTION,      true);
            put(LOGACCELEROMETER,   true);
            put(LOGGYROSCOPE,       true);
            put(LOGGRAVITY,         true);
        }
    }

    public final static String SAMPLINGTIME     = "samplingtime";
    public final static String TIMEOUT          = "timeout";
    public final static String SCHEDULERINTERVAL= "schedulerinterval";
    public final static String BATTERYTHRESHOLD = "batterythreshold";
    public final static String CALIBRATIONTIME  = "calibrationtime";

    public static class IntegerSettings extends HashMap<String, Integer> {
        IntegerSettings() {
            put(SAMPLINGTIME,       0);
            put(TIMEOUT,            0);
            put(SCHEDULERINTERVAL,  0);
            put(BATTERYTHRESHOLD,   20);
            put(CALIBRATIONTIME,    0);
        }
    }

    public static class IntegerSettingsRanges extends HashMap<String, IntegerRange> {
        IntegerSettingsRanges() {
            put(SAMPLINGTIME,       new IntegerRange(0, 250, 5));
            put(TIMEOUT,            new IntegerRange(0, 1500*1000, 10*1000));
            put(SCHEDULERINTERVAL,  new IntegerRange(0, 50*60000, 60000));
            put(BATTERYTHRESHOLD,   new IntegerRange(5, 95, 5));
            put(CALIBRATIONTIME,    new IntegerRange(0, 10*60000, 60000));
        }
    }

    public static class IntegerRange {

        private Integer[] mValues;
        private int mMin, mMax, mStep;

        IntegerRange(int min, int max, int step) {

            // TODO: error checking
            mMin = min;
            mMax = max;
            mStep = step;

            mValues = new Integer[(int) Math.ceil((max-min)/step)];
            for (int i=0; i<mValues.length; i++) mValues[i] = i*step+min;
        }

        public Integer[] getValues() {
            return mValues;
        }

        public Integer[] getValues(int multiplier) {
            Integer[] valuesInUnit = new Integer[mValues.length];
            for (int i=0; i<mValues.length; i++) valuesInUnit[i] = mValues[i]/multiplier;
            return valuesInUnit;
        }
    }

    public final static Map<String, String> UNITS = new HashMap<String, String>() {{
        put(SAMPLINGTIME,       "ms");
        put(TIMEOUT,            "s");
        put(SCHEDULERINTERVAL,  "min");
        put(BATTERYTHRESHOLD,   "%");
        put(CALIBRATIONTIME,    "min");
    }};

    public final static Map<String, Integer> MULTIPLIERS = new HashMap<String, Integer>() {{
        put("ms",   1);
        put("s",    1000);
        put("min",  60000);
    }};

    // Other string constants
    public final static String BROADCAST_ACCELEROMETER  = "broadcast-accelerometer";
    public final static String BROADCAST_GYROSCOPE      = "broadcast-gyroscope";
    public final static String BROADCAST_STARTED        = "broadcast-started";
    public final static String BROADCAST_STOPPED        = "broadcast-stopped";

    public final static String MESSAGE_ACCELEROMETER    = "message-accelerometer";
    public final static String MESSAGE_GYROSCOPE        = "message-gyroscope";

    public final static String LOG  = "log";

    // Interfaces
    public interface Listener {
        void onServiceStarted();
        void onServiceStopped();
        void onIntChanged(String identifier, int value);
        void onIntChangedInUnit(String identifier, int value);
        void onReadout(String message);
    }

    // Private Members
    private Context mContext;

    private ArrayList<Listener> mListenerList = new ArrayList<>();

    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            receiveBroadcast(context, intent);
        }
    };

    // Settings
    private BooleanSettings mBooleanSettings = new BooleanSettings();
    private IntegerSettings mIntegerSettings = new IntegerSettings();
    private IntegerSettingsRanges mIntegerSettingsRanges = new IntegerSettingsRanges();

    // Constructors
    SensorLogger(Context context) {
        mContext = context;
    }

    SensorLogger(Context context, Bundle previousState) {
        mContext = context;
        loadState(previousState);
    }

    SensorLogger(Context context, SharedPreferences preferences) {
        mContext = context;
        loadState(preferences);
    }

    // Public methods
    public boolean registerListener (Listener listener) {
        mListenerList.add(listener);
        return true;
    }

    public boolean isRunning() {

        ActivityManager manager =
                (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service
                : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SensorLoggerService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public boolean startLog() {

        // Start broadcast receivers
        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).registerReceiver(
                mMessageReceiver, new IntentFilter(BROADCAST_ACCELEROMETER));
        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).registerReceiver(
                mMessageReceiver, new IntentFilter(BROADCAST_GYROSCOPE));
        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).registerReceiver(
                mMessageReceiver, new IntentFilter(BROADCAST_STARTED));
        LocalBroadcastManager.getInstance(mContext.getApplicationContext()).registerReceiver(
                mMessageReceiver, new IntentFilter(BROADCAST_STOPPED));

        // Create intent
        Intent intent = new Intent(mContext.getApplicationContext(), SensorLoggerService.class);
        for (Map.Entry<String, Integer> entry : mIntegerSettings.entrySet())
            intent.putExtra(entry.getKey(), entry.getValue());
        for (Map.Entry<String, Boolean> entry : mBooleanSettings.entrySet())
            intent.putExtra(entry.getKey(), entry.getValue());

        if (!isRunning()) intent.putExtra(LOG, true);

        // Start logger service
        mContext.startService(intent);

        return true;
    }

    public boolean stopLog() {

        // Create intent
        Intent intent = new Intent(mContext.getApplicationContext(), SensorLoggerService.class);

        // Stop logger service
        mContext.stopService(intent);

        return true;
    }

    public void loadState(Bundle previousState) {

        if (previousState != null) {
            for (Map.Entry<String, Integer> entry : mIntegerSettings.entrySet())
                mIntegerSettings.put(entry.getKey(),
                        previousState.getInt(entry.getKey(), entry.getValue()));
            for (Map.Entry<String, Boolean> entry : mBooleanSettings.entrySet())
                mBooleanSettings.put(entry.getKey(),
                        previousState.getBoolean(entry.getKey(), entry.getValue()));
        }
    }

    public void loadState(SharedPreferences previousState) {

        if (previousState != null) {
            for (Map.Entry<String, Integer> entry : mIntegerSettings.entrySet())
                mIntegerSettings.put(entry.getKey(),
                        previousState.getInt(entry.getKey(), entry.getValue()));
            for (Map.Entry<String, Boolean> entry : mBooleanSettings.entrySet())
                mBooleanSettings.put(entry.getKey(),
                        previousState.getBoolean(entry.getKey(), entry.getValue()));
        }
    }

    public void saveState(Bundle outState) {

        for (Map.Entry<String, Integer> entry : mIntegerSettings.entrySet())
            outState.putInt(entry.getKey(), entry.getValue());
        for (Map.Entry<String, Boolean> entry : mBooleanSettings.entrySet())
            outState.putBoolean(entry.getKey(), entry.getValue());

    }

    public void saveState(SharedPreferences preferences) {

        SharedPreferences.Editor outState = preferences.edit();

        for (Map.Entry<String, Integer> entry : mIntegerSettings.entrySet())
            outState.putInt(entry.getKey(), entry.getValue());
        for (Map.Entry<String, Boolean> entry : mBooleanSettings.entrySet())
            outState.putBoolean(entry.getKey(), entry.getValue());

        outState.apply();

    }

    // set/get
    public boolean isInt(String identifier) {

        return mIntegerSettings.containsKey(identifier);

    }

    public int getInt(String identifier) {

        if (mIntegerSettings.containsKey(identifier))
            return mIntegerSettings.get(identifier);
        else
            return 0;
    }

    public int getIntInUnit(String identifier) {

        if (mIntegerSettings.containsKey(identifier)) {

            if (MULTIPLIERS.containsKey(UNITS.get((identifier))))
                return mIntegerSettings.get(identifier)/MULTIPLIERS.get(UNITS.get((identifier)));
            else
                return mIntegerSettings.get(identifier);

        } else return 0;
    }

    public boolean setInt(String identifier, int value) {

        Integer[] range = mIntegerSettingsRanges.get(identifier).getValues();

        if (Arrays.asList(range).contains(value)) {

            mIntegerSettings.put(identifier, value);

            for (Listener ll : mListenerList) ll.onIntChanged(identifier, value);
            if (isRunning()) startLog();

            return true;

        } else return false;

    }

    public boolean setIntInUnit(String identifier, int value) {

        if (!MULTIPLIERS.containsKey(UNITS.get(identifier)))
            return setInt(identifier, value);

        int multiplier = MULTIPLIERS.get(UNITS.get(identifier));

        Integer[] range = mIntegerSettingsRanges.get(identifier).getValues();

        if (Arrays.asList(range).contains(value*multiplier)) {

            mIntegerSettings.put(identifier, value*multiplier);

            for (Listener ll : mListenerList) ll.onIntChangedInUnit(identifier, value);
            if (isRunning()) startLog();

            return true;

        } else return false;

    }

    public Integer[] getIntRange(String identifier) {

        if (mIntegerSettingsRanges.containsKey(identifier))
            return mIntegerSettingsRanges.get(identifier).getValues();
        else
            return new Integer[0];

    }

    public Integer[] getIntRangeInUnit(String identifier) {

        if (mIntegerSettingsRanges.containsKey(identifier)) {

            if (MULTIPLIERS.containsKey(UNITS.get((identifier))))
                return mIntegerSettingsRanges.get(identifier)
                        .getValues(MULTIPLIERS.get(UNITS.get((identifier))));
            else
                return mIntegerSettingsRanges.get(identifier).getValues();

        } else return new Integer[0];

    }

    public boolean setIntRange(String identifier, int min, int max, int step) {

        if (mIntegerSettingsRanges.containsKey(identifier)) {

            mIntegerSettingsRanges.put(identifier, new IntegerRange(min, max, step));
            return true;

        } else return false;

    }

    public String getUnit(String identifier) {

        return UNITS.get(identifier);

    }

    public boolean getBool(String identifier) {

        if (mBooleanSettings.containsKey(identifier))
            return mBooleanSettings.get(identifier);
        else
            return false;
    }

    public boolean setBool(String identifier, boolean value) {

        if (mBooleanSettings.containsKey(identifier)) {

            mBooleanSettings.put(identifier, value);
            return true;

        } else return false;

    }

    public String[] getIdentifiers() {

        ArrayList<String> identifierList = new ArrayList<>();
        identifierList.addAll(new ArrayList<>(mIntegerSettings.keySet()));
        identifierList.addAll(new ArrayList<>(mBooleanSettings.keySet()));

        String[] identifiers = new String[identifierList.size()];

        return identifierList.toArray(identifiers);
    }

    // Private methods
    private void receiveBroadcast(Context context, Intent intent) {

        String action = intent.getAction();

        for (Listener ll : mListenerList) {

            switch (action) {

                case BROADCAST_ACCELEROMETER:
                    ll.onReadout(intent.getStringExtra(MESSAGE_ACCELEROMETER));
                    break;

                case BROADCAST_GYROSCOPE:
                    ll.onReadout(intent.getStringExtra(MESSAGE_GYROSCOPE));
                    break;

                case BROADCAST_STARTED:
                    ll.onServiceStarted();
                    break;

                case BROADCAST_STOPPED:
                    ll.onServiceStopped();
                    break;
            }
        }

        if (action.equals(BROADCAST_STOPPED)) {

            // Stop broadcast receiver
            LocalBroadcastManager
                    .getInstance(mContext.getApplicationContext())
                    .unregisterReceiver(mMessageReceiver);

        }
    }
}
