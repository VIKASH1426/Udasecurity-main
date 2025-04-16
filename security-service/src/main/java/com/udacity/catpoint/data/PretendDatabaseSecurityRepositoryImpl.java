package com.udacity.catpoint.data;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Type;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public class PretendDatabaseSecurityRepositoryImpl implements SecurityRepository{
    private Set<Sensor> sensors = new HashSet<>();
    private AlarmStatus alarmStatus = AlarmStatus.NO_ALARM;
    private ArmingStatus armingStatus = ArmingStatus.DISARMED;
    private static final String SENSORS = "SENSORS";
    private static final String ALARM_STATUS = "ALARM_STATUS";
    private static final String ARMING_STATUS = "ARMING_STATUS";
    private static final Preferences prefs = Preferences.userNodeForPackage(PretendDatabaseSecurityRepositoryImpl.class);
    private static final Gson gson = new Gson();
    private static final Logger log = LoggerFactory.getLogger(PretendDatabaseSecurityRepositoryImpl.class);
    public PretendDatabaseSecurityRepositoryImpl(){
        try{
            alarmStatus = AlarmStatus.valueOf(prefs.get(ALARM_STATUS, AlarmStatus.NO_ALARM.toString()));
            armingStatus = ArmingStatus.valueOf(prefs.get(ARMING_STATUS, ArmingStatus.DISARMED.toString()));
            String sensorString = prefs.get(SENSORS, null);
            if (sensorString == null || sensorString.isBlank()){
                sensors = new TreeSet<>();
            } else {
                Type type = new TypeToken<Set<Sensor>>() {}.getType();
                Set<Sensor> loadedSensors = gson.fromJson(sensorString, type);
                sensors = (loadedSensors != null) ? new TreeSet<>(loadedSensors) : new TreeSet<>();
            }
        } catch (RuntimeException e) {
            log.error("Failed to load state from preferences, using defaults. Error: {}", e.getMessage(), e);
            alarmStatus = AlarmStatus.NO_ALARM;
            armingStatus = ArmingStatus.DISARMED;
            sensors = new TreeSet<>();
        }
        if (sensors == null) {
            sensors = new TreeSet<>();
            log.warn("Sensor set was unexpectedly null after loading, initialized to empty set.");
        }
    }
    private void savePrefs(String key, String value){
        try{
            prefs.put(key, value);
            prefs.flush();
        } catch (SecurityException | BackingStoreException e) {
            log.error("Failed to save preference {} : {}", key, e.getMessage(), e);
        }
    }
    @Override
    public synchronized void addSensor(Sensor sensor){
        if (sensor != null){
            if (sensors.add(sensor)){
                savePrefs(SENSORS, gson.toJson(sensors));
            }
        }
    }
    @Override
    public synchronized void removeSensor(Sensor sensor){
        if (sensor != null){
            if (sensors.remove(sensor)){
                savePrefs(SENSORS, gson.toJson(sensors));
            }
        }
    }
    @Override
    public synchronized void updateSensor(Sensor sensor) {
        if (sensor != null) {
            if (sensors.remove(sensor)) {
                sensors.add(sensor);
                savePrefs(SENSORS, gson.toJson(sensors));
            } else{
                log.warn("Attempted to update sensor not found in the set: {}", sensor.getName());
            }
        }
    }
    @Override
    public synchronized void setAlarmStatus(AlarmStatus alarmStatus){
        if (alarmStatus != null && this.alarmStatus != alarmStatus){
            this.alarmStatus = alarmStatus;
            savePrefs(ALARM_STATUS, this.alarmStatus.toString());
        }
    }
    @Override
    public synchronized void setArmingStatus(ArmingStatus armingStatus){
        if (armingStatus != null && this.armingStatus != armingStatus){
            this.armingStatus = armingStatus;
            savePrefs(ARMING_STATUS, this.armingStatus.toString());
        }
    }
    @Override
    public synchronized Set<Sensor> getSensors(){
        return Set.copyOf(sensors);
    }
    @Override
    public synchronized AlarmStatus getAlarmStatus() {
        return alarmStatus;
    }
    @Override
    public synchronized ArmingStatus getArmingStatus() { // Added synchronized
        return armingStatus;
    }
}