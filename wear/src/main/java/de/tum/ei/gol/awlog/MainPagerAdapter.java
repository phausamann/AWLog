package de.tum.ei.gol.awlog;

import android.app.Fragment;
import android.app.FragmentManager;
import android.support.wearable.view.FragmentGridPagerAdapter;

/**
 * Created by Peter on 22.02.2016.
 */
public class MainPagerAdapter extends FragmentGridPagerAdapter {

    public enum Pages {
        PAGE_STARTSTOP,
        PAGE_SETTINGS,
    };

    public MainPagerAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public Fragment getFragment(int row, int col) {

        switch (MainPagerAdapter.Pages.values()[col]) {
            case PAGE_STARTSTOP:
                return new StartStopFragment();
            case PAGE_SETTINGS:
                return new SettingSelectorFragment();
        }

        return null;
    }

    @Override
    public int getRowCount() {
        return 1;
    }

    @Override
    public int getColumnCount(int i) {
        return MainPagerAdapter.Pages.values().length;
    }
}
