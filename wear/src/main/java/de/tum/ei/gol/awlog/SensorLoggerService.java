package de.tum.ei.gol.awlog;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.provider.Settings;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.util.SparseArray;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * Created by Peter on 22.02.2016.
 */
public class SensorLoggerService extends Service implements SensorEventListener {

    // Universal constants
    public final static int MILLITONANO = 1000000;
    public final static int MAX_SENSORS = 3;
    public final static int MAX_VALUES = 3;

    // Keys
    public final static String KEY_WAKELOCK = "key-wakelock";

    // Maps
    public final static SparseArray<String> PREFIX = new SparseArray<String>(MAX_SENSORS) {{
        put(Sensor.TYPE_ACCELEROMETER,  "prefix_acc");
        put(Sensor.TYPE_GYROSCOPE,      "prefix_gyr");
        put(Sensor.TYPE_GRAVITY,        "prefix_grv");
    }};

    // Notifications
    public final static int ID_NOTIFICATION = 1; // TODO: make sure ID is unique

    // Sensors
    private SensorManager mSensorManager;
    private Sensor mAccelerometer, mGyroscope, mGravitySensor, mMotionSensor;
    private TriggerEventListener mMotionListener = new TriggerEventListener() {
        @Override
        public void onTrigger(TriggerEvent event) {
            onMotionTrigger();
        }
    };

    // Wakelock, battery monitor, timeout & rescheduler
    private PowerManager.WakeLock mWakeLock;
    private Handler mTimeoutHandler = new Handler();
    private Runnable mTimeoutRunnable;
    private AlarmManager mScheduler;
    private long mNextLogTime;

    // Log file
    private String mDeviceID;
    private String mDirectory;
    private FileOutputStream mOutputStream = null;

    // State
    private boolean mRunning = false;
    private boolean mSuspended = true;
    private boolean mFileOpen = false;
    private boolean mCalibrated = false;

    // Hard-coded (so far)
    private boolean mAppend = false;  //TODO: check this
    private boolean mStrictUpperBound = true;

    // User settings
    private SensorLogger.BooleanSettings mBooleanSettings = new SensorLogger.BooleanSettings();
    private SensorLogger.IntegerSettings mIntegerSettings = new SensorLogger.IntegerSettings();

    // Storage
    private SparseArray<Long> mLastTimestamp = new SparseArray<Long>(MAX_SENSORS) {{
        put(Sensor.TYPE_ACCELEROMETER,  0L);
        put(Sensor.TYPE_GYROSCOPE,      0L);
        put(Sensor.TYPE_GRAVITY,        0L);
    }};

    private SparseArray<Long> mLastRecordedTimestamp = new SparseArray<Long>(MAX_SENSORS) {{
        put(Sensor.TYPE_ACCELEROMETER,  0L);
        put(Sensor.TYPE_GYROSCOPE,      0L);
        put(Sensor.TYPE_GRAVITY,        0L);
    }};

    private SparseArray<float[]> mLastValues = new SparseArray<float[]>(MAX_SENSORS) {{
        put(Sensor.TYPE_ACCELEROMETER,  new float[MAX_VALUES]);
        put(Sensor.TYPE_GYROSCOPE,      new float[MAX_VALUES]);
        put(Sensor.TYPE_GRAVITY,        new float[MAX_VALUES]);
    }};

    // Overrides
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Get input
        for (Map.Entry<String, Integer> entry : mIntegerSettings.entrySet())
            mIntegerSettings.put(entry.getKey(),
                    intent.getIntExtra(entry.getKey(), entry.getValue()));
        for (Map.Entry<String, Boolean> entry : mBooleanSettings.entrySet())
            mBooleanSettings.put(entry.getKey(),
                    intent.getBooleanExtra(entry.getKey(), entry.getValue()));

        // Check for log request
        if (!intent.getBooleanExtra(SensorLogger.LOG, false))
            return START_STICKY;

        // Check if already logging
        if (mRunning && !mSuspended)
            return START_STICKY;

        // Initialize
        if (!mRunning) {

            // Initialize environment
            initSensors();

            // Get id
            mDeviceID = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

            // Notify listeners
            LocalBroadcastManager.getInstance(this)
                    .sendBroadcast(new Intent(SensorLogger.BROADCAST_STARTED));

            Log.d(getString(R.string.app_name), "Service started");
            mRunning = true;
        }

        if(startLogger())
            return START_NOT_STICKY;
        else
            return START_STICKY;
    }

    @Override
    public void onCreate(){
        super.onCreate();
    }

    @Override
    public void onDestroy(){

        mSuspended = true;
        mRunning = false;

        // Shut down environment
        if (mSensorManager != null) mSensorManager.unregisterListener(this);
        closeOutputStream();
        cancelTimeout();
        cancelScheduler();
        releaseWakeLock();

        // Notify listeners
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(SensorLogger.BROADCAST_STOPPED));

        Log.w(getString(R.string.app_name), "Service stopped");

        super.onDestroy();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        int type = event.sensor.getType();

        // Return if logging is suspended
        if (mSuspended || !mRunning || !mFileOpen) return;

        long timestamp = event.timestamp;
        float[] values = event.values;

        String prefix = getString(
                getResources().getIdentifier(PREFIX.get(type), "string", getPackageName()));
        long lastTimestamp = mLastTimestamp.get(type);
        long lastRecordedTimestamp = mLastRecordedTimestamp.get(type);

        int samplingTime = mIntegerSettings.get(SensorLogger.SAMPLINGTIME);

        // Get data
        if ((timestamp - lastRecordedTimestamp) >= samplingTime*MILLITONANO) {

            // Use previous values if they are closer to the actual sampling time and there
            // hasn't been an interruption
            long offset = lastRecordedTimestamp+samplingTime*MILLITONANO;
            if (lastRecordedTimestamp != 0 && lastRecordedTimestamp != lastTimestamp &&
                    (Math.abs(timestamp-offset) > Math.abs(lastTimestamp-offset)||
                    mStrictUpperBound)) {
                timestamp = lastTimestamp;
                values = mLastValues.get(type);
            }

            // Write to stream
            float diff = (float) ((double) timestamp - lastRecordedTimestamp) / MILLITONANO;
            writeToStream(buildDataString(prefix, timestamp, diff, values, event.accuracy));
            mLastRecordedTimestamp.put(type, timestamp);

        } else {

            // Store previous readout
            mLastValues.put(type, values);
            mLastTimestamp.put(type, timestamp);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Sensors
    private boolean initSensors() {

        if (mSensorManager == null) {

            int sensorDelay = SensorManager.SENSOR_DELAY_FASTEST;

            mSensorManager  = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

            mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mSensorManager.registerListener(this, mAccelerometer, sensorDelay);

            mGyroscope      = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mSensorManager.registerListener(this, mGyroscope, sensorDelay);

            mGravitySensor  = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);
            mSensorManager.registerListener(this, mGravitySensor, sensorDelay);

            mMotionSensor   = mSensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION);

            if (mMotionSensor != null && mBooleanSettings.get(SensorLogger.STARTONMOTION))
                mSensorManager.requestTriggerSensor(mMotionListener, mMotionSensor);

            return true;

        } else return false;
    }

    // Startup routine
    private boolean startLogger() {

        if (!mRunning)
            return false;

        // Check battery
        if (getBatteryLevel() < ((float) mIntegerSettings.get(SensorLogger.BATTERYTHRESHOLD))/100) {
            Log.w(getString(R.string.app_name), "Battery low (" +
                    String.valueOf(100*getBatteryLevel()) + "%). Stopping service...");
            stopSelf();
            return false;
        }

        // Open file
        if (!openOutputStream()) {
            Log.e(getString(R.string.app_name), "Couldn't open output stream");
            stopSelf();
            return false;
        }

        mSuspended = false;

        // Calibrate
        if (mIntegerSettings.get(SensorLogger.CALIBRATIONTIME) > 0 && !mCalibrated) {

            startCalibration();

            Log.d(getString(R.string.app_name), "Calibration started");

        } else { // Set timeout, wakelock and scheduler

            if (mBooleanSettings.get(SensorLogger.USEWAKELOCK)) acquireWakeLock();
            else releaseWakeLock();

            if (mIntegerSettings.get(SensorLogger.SCHEDULERINTERVAL) > 0) setScheduler();
            else cancelScheduler();

            if (mIntegerSettings.get(SensorLogger.TIMEOUT) > 0) setTimeout();

            Log.d(getString(R.string.app_name), "Log started");
        }

        // Start service
        startForeground(ID_NOTIFICATION, getNotification());

        return true;

    }

    private boolean startCalibration() {

        acquireWakeLock();

        Runnable calibrationRunnable = new Runnable() {

            @Override
            public void run() {

                Log.d(getString(R.string.app_name), "Calibration finished");
                if (!mAppend) closeOutputStream();

                releaseWakeLock();

                mCalibrated = true;
                mSuspended = true;

                startForeground(ID_NOTIFICATION, getNotification());
                startLogger();
            }
        };

        mTimeoutHandler.postDelayed(
                calibrationRunnable, mIntegerSettings.get(SensorLogger.CALIBRATIONTIME));

        return true;

    }

    private boolean onMotionTrigger() {

        // Return if not running or no trigger wanted
        if (!mRunning || !mBooleanSettings.get(SensorLogger.STARTONMOTION)) return false;

        // Already running
        if (!mSuspended && mIntegerSettings.get(SensorLogger.TIMEOUT) > 0) {
            mSensorManager.requestTriggerSensor(mMotionListener, mMotionSensor);
            return true;
        }

        // Postpone scheduler and start log
        cancelScheduler();
        startForeground(ID_NOTIFICATION, getNotification());
        startLogger();

        writeToStream("Motion-triggered logging\n");
        Log.i(getString(R.string.app_name), "Motion-triggered logging");

        mSensorManager.requestTriggerSensor(mMotionListener, mMotionSensor);

        return true;

    }

    // Stream
    private boolean openOutputStream() {

        // Return if already open
        if (mFileOpen) return true;

        // Get filename
        String mFilename;
        if (mBooleanSettings.get(SensorLogger.UNIQUENAME) ||
                mIntegerSettings.get(SensorLogger.SCHEDULERINTERVAL) > 0 && !mAppend) {
            mFilename = mDeviceID + "_" + getDateString() + "." + getString(R.string.extension);
        } else
            mFilename = mDeviceID + "." + getString(R.string.extension);

        // Get path
        if (mDirectory == null) {
            if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
                mDirectory = getExternalFilesDir(null).getAbsolutePath();
            else
                mDirectory = getFilesDir().getAbsolutePath();

            Log.d(getString(R.string.app_name), "Files directory: " + mDirectory);
        }

        // Open stream
        try {
            mOutputStream = new FileOutputStream(mDirectory + File.separator + mFilename, mAppend);
            mFileOpen = true;

        } catch (Exception e) {
            e.printStackTrace();
            mOutputStream = null;
            mFileOpen = false;
            return false;
        }

        // Write starting point to stream
        return writeToStream("Started logging in " + mIntegerSettings.get(SensorLogger.SAMPLINGTIME)
                + " ms intervals on " + getDateString() + "\n");
    }

    private boolean closeOutputStream () {

        Log.d(getString(R.string.app_name), "Stream closed");

        // Write stopping point to stream
        if (!writeToStream("Stopped logging on " + getDateString() + "\n")) {
            mFileOpen = false;
            return false;
        }

        mFileOpen = false;

        // Close stream
        try {
            mOutputStream.close();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            mOutputStream = null;
            return false;
        }
    }

    // Timeout
    private boolean setTimeout() {

        // Timeout reached
        if (mTimeoutRunnable == null)

            mTimeoutRunnable = new Runnable() {

                @Override
                public void run() {

                    Log.d(getString(R.string.app_name), "Timeout");
                    if (!mAppend) closeOutputStream();
                    releaseWakeLock();

                    mSuspended = true;

                    startForeground(ID_NOTIFICATION, getNotification());
                }
            };

        if (!mSuspended) {

            mTimeoutHandler.postDelayed(
                    mTimeoutRunnable, mIntegerSettings.get(SensorLogger.TIMEOUT));
            return true;

        } else return false;

    }

    private boolean cancelTimeout() {

        mTimeoutHandler.removeCallbacksAndMessages(null);

        return true;

    }

    // Scheduler
    private boolean setScheduler() {

        if (mScheduler == null)
            mScheduler = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(getApplicationContext(), SensorLoggerService.class);
        intent.putExtra(SensorLogger.LOG, true);
        PendingIntent scheduledIntent = PendingIntent.getService(getApplicationContext(),
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        mNextLogTime =
                System.currentTimeMillis()+mIntegerSettings.get(SensorLogger.SCHEDULERINTERVAL);
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(mNextLogTime,
                pendingIntent);
        mScheduler.setAlarmClock(info, scheduledIntent);

        writeToStream("Next log scheduled at " + getDateString(mNextLogTime) + "\n");
        Log.d(getString(R.string.app_name), "Next log scheduled at " + getDateString(mNextLogTime));

        return true;

    }

    private boolean cancelScheduler () {

        if (mScheduler != null) {

            Intent intent = new Intent(getApplicationContext(), SensorLoggerService.class);
            PendingIntent scheduledIntent = PendingIntent.getService(getApplicationContext(),
                    0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            mScheduler.cancel(scheduledIntent);

            return true;

        } else return false;

    }

    // Wakelock
    private boolean acquireWakeLock() {

        // Initialize wakelock object
        if(mWakeLock == null) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, KEY_WAKELOCK);
        }

        // Acquire wakelock
        if (!mSuspended && !mWakeLock.isHeld()) {

            mWakeLock.acquire();
            Log.d(getString(R.string.app_name), "Wakelock acquired");
            return true;

        } else return false;
    }

    private boolean releaseWakeLock() {

        // Release wakelock
        if (mWakeLock != null && mWakeLock.isHeld()) {

            mWakeLock.release();
            Log.d(getString(R.string.app_name), "Wakelock released");
            return true;

        } else return false;
    }

    // Logging
    private boolean writeToStream(String data) {

        if (mFileOpen) {

            try {
                mOutputStream.write(data.getBytes());
                return true;

            } catch (Exception e) {
                e.printStackTrace();
                mFileOpen = false;
                mRunning = false;
                return false;
            }

        } else return false;
    }

    private String buildDataString(String prefix, long timestamp, float diff, float[] values) {

        String separator = getString(R.string.separator);
        String format = getString(R.string.format_float);

        return prefix + separator + timestamp + separator +
                String.format(java.util.Locale.US, format, diff) + separator +
                String.format(java.util.Locale.US, format, values[0]) + separator +
                String.format(java.util.Locale.US, format, values[1]) + separator +
                String.format(java.util.Locale.US, format, values[2]) + separator + "\n";

    }

    private String buildDataString(
            String prefix, long timestamp, float diff, float[] values, int accuracy) {

        String separator = getString(R.string.separator);
        String format = getString(R.string.format_float);

        return prefix + separator + timestamp + separator +
                String.format(java.util.Locale.US, format, diff) + separator +
                String.format(java.util.Locale.US, format, values[0]) + separator +
                String.format(java.util.Locale.US, format, values[1]) + separator +
                String.format(java.util.Locale.US, format, values[2]) + separator +
                accuracy + "\n";

    }

    private String getDateString() {
        return String.format(getString(R.string.format_date_time), Calendar.getInstance());
    }

    private String getDateString(long timestampMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestampMillis);
        return String.format(getString(R.string.format_date_time), calendar);
    }

    private String getTimeString(long timestampMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestampMillis);
        return String.format(getString(R.string.format_time), calendar);
    }

    // Other
    private float getBatteryLevel() {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            return level / (float) scale;
        } else
            return -1;
    }

    private Notification getNotification() {

        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        // TODO: logo
        int resourcesIdentifier =
                getResources().getIdentifier("start_button", "drawable", getPackageName());

        String notificationText;

        if(mSuspended && mIntegerSettings.get(SensorLogger.SCHEDULERINTERVAL) > 0)
            notificationText = String.format(getString(R.string.notification_nextlog),
                    getTimeString(mNextLogTime));
        else if (mSuspended)
            notificationText = getString(R.string.notification_suspended);
        else
            notificationText = getString(R.string.notification_logging);

        return new Notification.Builder(this)
                .setContentTitle(getText(R.string.app_name))
                .setContentText(notificationText)
                .setSmallIcon(resourcesIdentifier)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }
}
