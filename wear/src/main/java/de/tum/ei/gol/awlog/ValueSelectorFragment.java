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
public class ValueSelectorFragment extends Fragment implements WearableListView.ClickListener {

    public static final String IDENTIFIER = "identifier";
    private final static String LIST_POSITION = "list-postion";

    SensorLoggerInterface mSensorLoggerInterface;

    private String mIdentifier, mTitle, mUnit;

    private View mView;
    private TextView mTitleView, mUnitView;
    private WearableListView mListView;

    private int mListPosition = 0;

    // Constructor
    public static ValueSelectorFragment newInstance(String identifier) {

        ValueSelectorFragment settingsFragment = new ValueSelectorFragment();

        Bundle args = new Bundle();
        args.putString(IDENTIFIER, identifier);
        settingsFragment.setArguments(args);

        return settingsFragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {

        mIdentifier = getArguments().getString(IDENTIFIER);
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

        mIdentifier = getArguments().getString(IDENTIFIER);
        mTitle = getString(getResources().getIdentifier(mIdentifier, "string",
                context.getPackageName()));
        mUnit = mSensorLoggerInterface.getUnit(mIdentifier);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.valueselector, container, false);

        mTitleView = (TextView) mView.findViewById(R.id.values_title);
        mTitleView.setText(mTitle);

        mUnitView = (TextView) mView.findViewById(R.id.values_unit);
        mUnitView.setText(mUnit);

        if (savedInstanceState != null)
            mListPosition = savedInstanceState.getInt(LIST_POSITION, mListPosition);

        mListView = (WearableListView) mView.findViewById(R.id.values_list);
        mListView.setAdapter(new SettingsListAdapter());
        mListView.setClickListener(ValueSelectorFragment.this);
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

        mListPosition = viewHolder.getAdapterPosition();
        TextView textView = (TextView) viewHolder.itemView.findViewById(R.id.item);
        if(textView.getTag() != null) {
            if (mSensorLoggerInterface.setIntInUnit(mIdentifier, (int) textView.getTag())) {
                textView.setTextColor(getResources().getColor(R.color.green));
            }
        }

        View viewAbove = mListView.getLayoutManager().findViewByPosition(mListPosition - 1);
        if (viewAbove != null) {
            TextView textViewAbove = (TextView) viewAbove.findViewById(R.id.item);
            if (textViewAbove != null)
                textViewAbove.setTextColor(getResources().getColor(R.color.white));
        }

        View viewBelow = mListView.getLayoutManager().findViewByPosition(mListPosition +1);
        if (viewBelow != null) {
            TextView textViewBelow = (TextView) viewBelow.findViewById(R.id.item);
            if (textViewBelow != null)
                textViewBelow.setTextColor(getResources().getColor(R.color.white));
        }

        SettingSelectorFragment newFragment = new SettingSelectorFragment();
        this.getFragmentManager().beginTransaction()
                .replace(R.id.settings, newFragment, null)
                .addToBackStack(null)
                .commit();
    }

    @Override
    public void onTopEmptyRegionClick() {

    }

    @Override
    public void onHiddenChanged(boolean hidden) {

        if (hidden) {
            SettingSelectorFragment newFragment = new SettingSelectorFragment();
            this.getFragmentManager().beginTransaction()
                    .replace(R.id.settings, newFragment, null)
                    .addToBackStack(null)
                    .commit();
        }

    }

    private class SettingsListAdapter extends WearableListView.Adapter {

        @Override
        public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new WearableListView.ViewHolder(new MyItemView(parent.getContext()));
        }

        @Override
        public void onBindViewHolder(WearableListView.ViewHolder holder, int position) {
            TextView textView = (TextView) holder.itemView.findViewById(R.id.item);
            textView.setText(
                    mSensorLoggerInterface.getIntRangeInUnit(mIdentifier)[position].toString());
            textView.setTag(mSensorLoggerInterface.getIntRangeInUnit(mIdentifier)[position]);
        }

        @Override
        public int getItemCount() {
            if (mSensorLoggerInterface != null)
                return mSensorLoggerInterface.getIntRangeInUnit(mIdentifier).length;
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
            if(textView.getTag() != null && (int) textView.getTag() ==
                    mSensorLoggerInterface.getIntInUnit(mIdentifier))
                textView.setTextColor(getResources().getColor(R.color.green));
            else
                textView.setTextColor(getResources().getColor(R.color.white));
        }

        @Override
        public void onNonCenterPosition(boolean b) {
            TextView textView = (TextView) findViewById(R.id.item);
            textView.setTypeface(null, Typeface.NORMAL);
            if(textView.getTag() != null && (int) textView.getTag() ==
                    mSensorLoggerInterface.getIntInUnit(mIdentifier))
                textView.setTextColor(getResources().getColor(R.color.green));
            else
                textView.setTextColor(getResources().getColor(R.color.white));
        }
    }
}
