package de.tum.ei.gol.awlog;

import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

/**
 * Created by Peter on 22.02.2016.
 */
public class StartStopFragment extends Fragment implements SensorLogger.Listener{

    private View mView;
    private Button mButton;

    private SensorLoggerInterface mSensorLoggerInterface;

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

        mSensorLoggerInterface.registerListener(this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        mView = inflater.inflate(R.layout.startstop, container, false);
        mButton = (Button) mView.findViewById(R.id.startstop_button);

        if (mSensorLoggerInterface.isRunning()) {
            mButton.setBackgroundResource(R.drawable.stop_button);
            mButton.setText("Stop");
        } else {
            mButton.setBackgroundResource(R.drawable.start_button);
            mButton.setText("Start");
        }

        mButton.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (mSensorLoggerInterface.isRunning())
                    mSensorLoggerInterface.stopLog();
                else
                    mSensorLoggerInterface.startLog();
            }
        });

        return mView;
    }

    @Override
    public void onIntChanged(String identifier, int value) {

    }

    @Override
    public void onIntChangedInUnit(String identifier, int value) {

    }

    @Override
    public void onServiceStarted() {
        mButton.setBackgroundResource(R.drawable.stop_button);
        mButton.setText("Stop");
    }

    @Override
    public void onServiceStopped() {
        mButton.setBackgroundResource(R.drawable.start_button);
        mButton.setText("Start");
    }

    @Override
    public void onReadout(String message) {

    }
}
