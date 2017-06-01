package de.tum.ei.gol.awlog;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.wearable.view.DotsPageIndicator;
import android.support.wearable.view.GridViewPager;
import android.support.wearable.view.WatchViewStub;

// BTDB
// adb forward tcp:4444 localabstract:/adb-hub
// adb connect 127.0.0.1:4444
// adb -s 127.0.0.1:4444 install wear/build/outputs/apk/wear-debug.apk
// adb -s 127.0.0.1:4444 ls /storage/emulated/0/Android/data/de.tum.ei.gol.awlog/files
// adb -s 127.0.0.1:4444 shell rm -f /storage/emulated/0/Android/data/de.tum.ei.gol.awlog/files/*
// adb -s 127.0.0.1:4444 pull /storage/emulated/0/Android/data/de.tum.ei.gol.awlog/files dumps/files/

public class MainActivity extends Activity implements SensorLoggerInterface {

    private SensorLogger mSensorLogger;
    private String mSettingsPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mSensorLogger = new SensorLogger(this, savedInstanceState);
        } else {
            SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
            mSensorLogger = new SensorLogger(this, preferences);
        }

        setContentView(R.layout.activity_main);

        WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {

                final GridViewPager pager = (GridViewPager) findViewById(R.id.pager);
                DotsPageIndicator indicator = (DotsPageIndicator) findViewById(R.id.page_indicator);

                pager.setAdapter(new MainPagerAdapter(getFragmentManager()));

                GridViewPager.OnPageChangeListener pageListener =
                    new GridViewPager.OnPageChangeListener() {
                        @Override
                        public void onPageScrolled(int i, int i1, float v, float v1, int i2, int i3) {

                        }

                        @Override
                        public void onPageSelected(int i, int i1) {
                            if (MainPagerAdapter.Pages.values()[i] ==
                                    MainPagerAdapter.Pages.PAGE_STARTSTOP) {
                                SettingSelectorFragment newFragment =
                                        new SettingSelectorFragment();
                                getFragmentManager().beginTransaction()
                                        .replace(R.id.settings, newFragment, null)
                                        .addToBackStack(null)
                                        .commit();
                            }

                        }

                        @Override
                        public void onPageScrollStateChanged(int i) {

                        }
                    };

                indicator.setPager(pager);
                indicator.setOnPageChangeListener(pageListener);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mSensorLogger.isRunning())
            mSensorLogger.startLog();
    }

    @Override
    public void onPause() {
        SharedPreferences preferences = getPreferences(Context.MODE_PRIVATE);
        mSensorLogger.saveState(preferences);
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mSensorLogger.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRestoreInstanceState(Bundle savedState) {
        super.onRestoreInstanceState(savedState);
        mSensorLogger.loadState(savedState);
    }

    // SensorLoggerInterface
    @Override
    public boolean isRunning() {
        return mSensorLogger.isRunning();
    }

    @Override
    public boolean isInt(String identifier) {
        return mSensorLogger.isInt(identifier);
    }

    @Override
    public int getInt(String identifier) {
        return mSensorLogger.getInt(identifier);
    }

    @Override
    public int getIntInUnit(String identifier) {
        return mSensorLogger.getIntInUnit(identifier);
    }

    @Override
    public boolean setInt(String identifier, int value) {
        return mSensorLogger.setInt(identifier, value);
    }

    @Override
    public boolean setIntInUnit(String identifier, int value) {
        return mSensorLogger.setIntInUnit(identifier, value);
    }

    @Override
    public Integer[] getIntRange(String identifier) {
        return mSensorLogger.getIntRange(identifier);
    }

    @Override
    public Integer[] getIntRangeInUnit(String identifier) {
        return mSensorLogger.getIntRangeInUnit(identifier);
    }

    @Override
    public String getUnit(String identifier) {
        return mSensorLogger.getUnit(identifier);
    }

    @Override
    public boolean getBool(String identifier) {
        return mSensorLogger.getBool(identifier);
    }

    @Override
    public boolean setBool(String identifier, boolean value) {
        return mSensorLogger.setBool(identifier, value);
    }

    @Override
    public String[] getIdentifiers() {
        return mSensorLogger.getIdentifiers();
    }

    @Override
    public boolean resetToDefault() {
        return false;
    }

    @Override
    public boolean deleteFiles() {
        return false;
    }

    @Override
    public boolean startLog() {
        return mSensorLogger.startLog();
    }

    @Override
    public boolean stopLog() {
        return mSensorLogger.stopLog();
    }

    @Override
    public boolean registerListener(SensorLogger.Listener listener) {
        return mSensorLogger.registerListener(listener);
    }
}
