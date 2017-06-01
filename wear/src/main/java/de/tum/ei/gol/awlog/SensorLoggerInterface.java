package de.tum.ei.gol.awlog;

/**
 * Created by Peter on 24.02.2016.
 */
public interface SensorLoggerInterface {

    boolean isRunning();

    boolean isInt(String identifier);

    int getInt(String identifier);

    int getIntInUnit(String identifier);

    boolean setInt(String identifier, int value);

    boolean setIntInUnit(String identifier, int value);

    Integer[] getIntRange(String identifier);

    Integer[] getIntRangeInUnit(String identifier);

    String getUnit(String identifier);

    boolean getBool(String identifier);

    boolean setBool(String identifier, boolean value);

    String[] getIdentifiers();

    boolean resetToDefault();

    boolean deleteFiles();

    boolean startLog();

    boolean stopLog();

    boolean registerListener(SensorLogger.Listener listener);

}
