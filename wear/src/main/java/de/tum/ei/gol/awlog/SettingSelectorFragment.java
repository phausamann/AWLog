package de.tum.ei.gol.awlog;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.wearable.view.WearableListView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * Created by Peter on 22.02.2016.
 */
public class SettingSelectorFragment extends Fragment
        implements WearableListView.ClickListener, SensorLogger.Listener {

    private final static String LIST_POSITION = "list-postion";

    SensorLoggerInterface mSensorLoggerInterface;

    private Context mContext;
    private View mView;
    private TextView mTitleView, mValueView;
    private WearableListView mListView;

    private int mListPosition = 0;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Context context) {

        super.onAttach(context);

        // Makes sure that the container activity has implemented the interface
        try {
            mSensorLoggerInterface = (SensorLoggerInterface) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString()
                    + " must implement SensorLoggerInterface");
        }

        mContext = context;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.settingselector, container, false);

        mTitleView = (TextView) mView.findViewById(R.id.settings_title);
        mTitleView.setText(getString(R.string.settings));

        mValueView = (TextView) mView.findViewById(R.id.settings_unit);

        if (savedInstanceState != null)
            mListPosition = savedInstanceState.getInt(LIST_POSITION, mListPosition);

        mListView = (WearableListView) mView.findViewById(R.id.settings_list);
        mListView.setAdapter(new SettingsListAdapter());
        mListView.setClickListener(SettingSelectorFragment.this);
        mListView.setGreedyTouchMode(true);
        mListView.scrollToPosition(mListPosition);

        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(LIST_POSITION, mListPosition);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(WearableListView.ViewHolder viewHolder) {

        TextView textView = (TextView) viewHolder.itemView.findViewById(R.id.item);

        String identifier = (String) textView.getTag();

        if (mSensorLoggerInterface.isInt(identifier)) {

            ValueSelectorFragment newFragment = ValueSelectorFragment.newInstance(identifier);
            this.getFragmentManager().beginTransaction()
                    .replace(R.id.settings, newFragment, null)
                    .addToBackStack(null)
                    .commit();

        } else {
            mSensorLoggerInterface.setBool(identifier, !mSensorLoggerInterface.getBool(identifier));
            mValueView.setText(String.valueOf(mSensorLoggerInterface.getBool(identifier)));
        }
    }

    @Override
    public void onTopEmptyRegionClick() {

    }

    @Override
    public void onServiceStarted() {

    }

    @Override
    public void onServiceStopped() {

    }

    @Override
    public void onIntChanged(String identifier, int value) {

    }

    @Override
    public void onIntChangedInUnit(String identifier, int value) {

    }

    @Override
    public void onReadout(String message) {

    }

    private class SettingsListAdapter extends WearableListView.Adapter {

        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new WearableListView.ViewHolder(new MyItemView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
            TextView textView = (TextView) holder.itemView.findViewById(R.id.item);
            String identifier = mSensorLoggerInterface.getIdentifiers()[position];

            textView.setText(getString(getResources().getIdentifier(identifier, "string",
                        mContext.getPackageName())));

            textView.setTextAppearance(android.R.style.TextAppearance_Small);
            textView.setTag(identifier);
        }

        @Override
        public int getItemCount() {
            if (mSensorLoggerInterface != null)
                return mSensorLoggerInterface.getIdentifiers().length;
            else
                return 0;
        }
    }

    private final class MyItemView extends RelativeLayout implements
            WearableListView.OnCenterProximityListener {

        public MyItemView(Context context) {
            super(context);
            View.inflate(context, R.layout.list_item, this);
        }

        @Override
        public void onCenterPosition(boolean b) {
            TextView textView = (TextView) findViewById(R.id.item);
            textView.setTypeface(null, Typeface.BOLD);

            String identifier = (String) textView.getTag();

            if (mSensorLoggerInterface.isInt(identifier))
                mValueView.setText(String.format(getString(R.string.format_int_settings),
                        mSensorLoggerInterface.getIntInUnit(identifier),
                        mSensorLoggerInterface.getUnit(identifier)));
            else
                mValueView.setText(String.valueOf(mSensorLoggerInterface.getBool(identifier)));
        }

        @Override
        public void onNonCenterPosition(boolean b) {
            TextView textView = (TextView) findViewById(R.id.item);
            textView.setTypeface(null, Typeface.NORMAL);
        }
    }
}
