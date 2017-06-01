package de.tum.ei.gol.awlog;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Peter on 22.02.2016.
 */
public class PreviewFragment extends Fragment implements SensorLogger.Listener {

    SensorLoggerInterface mSensorLoggerInterface;

    private Context mContext;
    private View mView;
    private TextView mTextView;
    private String[] mStatusStringArray;
    private Map<String, String> mStatusStringMap = new HashMap<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.preview, container, false);
        mTextView = (TextView) mView.findViewById(R.id.preview_text);

        // TODO: map
        mStatusStringArray = new String[4];

        if (mSensorLoggerInterface.isRunning())
//            mStatusStringMap.put("running", "Running");
            mStatusStringArray[0] = "Running";
        else
//            mStatusStringMap.put("running", "Stopped");
            mStatusStringArray[0] = "Stopped";

        mStatusStringArray[1] = getString(R.string.samplingtime) + ": " +
                String.valueOf(mSensorLoggerInterface.getIntInUnit(SensorLogger.SAMPLINGTIME)) +
                " " + mSensorLoggerInterface.getUnit(SensorLogger.SAMPLINGTIME);
        mStatusStringArray[2] = getString(R.string.timeout) + ": " +
                String.valueOf(mSensorLoggerInterface.getIntInUnit(SensorLogger.TIMEOUT)) +
                " " + mSensorLoggerInterface.getUnit(SensorLogger.TIMEOUT);
        mStatusStringArray[3] = getString(R.string.schedulerinterval) + ": " +
                String.valueOf(mSensorLoggerInterface.getIntInUnit(SensorLogger.SCHEDULERINTERVAL))+
                " " + mSensorLoggerInterface.getUnit(SensorLogger.SCHEDULERINTERVAL);

        setStatusText();

        return mView;
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
        mSensorLoggerInterface.registerListener(this);
    }

    @Override
    public void onIntChanged(String identifier, int value) {
    }

    @Override
    public void onIntChangedInUnit(String identifier, int value) {

        if (isAdded()) {

            String string = getString(getResources().getIdentifier(identifier, "string",
                    mContext.getPackageName())) + ": " + String.valueOf(value) + " " +
                    mSensorLoggerInterface.getUnit(identifier);

            switch (identifier) {
                case SensorLogger.SAMPLINGTIME:
                    mStatusStringArray[1] = string;
                    break;
                case SensorLogger.TIMEOUT:
                    mStatusStringArray[2] = string;
                    break;
                case SensorLogger.SCHEDULERINTERVAL:
                    mStatusStringArray[3] = string;
                    break;
                default: return;
            }

            setStatusText();
        }

    }

    @Override
    public void onServiceStarted() {
        mStatusStringArray[0] = "Running";
    }

    @Override
    public void onServiceStopped() {
        mStatusStringArray[0] = "Stopped";
    }

    @Override
    public void onReadout(String message) {    }

    private void setStatusText() {
        String statusString = Arrays.toString(mStatusStringArray);
        statusString = statusString.substring(1, statusString.length()-1).replaceAll(",", "\n");
        mTextView.setText(statusString);
    }
}
