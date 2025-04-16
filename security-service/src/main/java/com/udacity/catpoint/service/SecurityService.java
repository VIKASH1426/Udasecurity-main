package com.udacity.catpoint.service;
import com.udacity.catpoint.image.ImageService;
import com.udacity.catpoint.application.StatusListener;
import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.data.ArmingStatus;
import com.udacity.catpoint.data.SecurityRepository;
import com.udacity.catpoint.data.Sensor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.awt.image.BufferedImage;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
public class SecurityService{
    private final ImageService imageService;
    private final SecurityRepository securityRepository;
    private final Set<StatusListener> statusListeners = new CopyOnWriteArraySet<>();
    private boolean catDetectedCurrently = false; // Flag to store cat detection status
    private static final Logger log = LoggerFactory.getLogger(SecurityService.class);
    public SecurityService(SecurityRepository securityRepository, ImageService imageService) {
        if (securityRepository == null){
            throw new IllegalArgumentException("SecurityRepository cannot be null");
        }
        if (imageService == null){
            throw new IllegalArgumentException("ImageService cannot be null");
        }
        this.securityRepository =securityRepository;
        this.imageService= imageService;
    }
    public synchronized void setArmingStatus(ArmingStatus armingStatus){
        if (armingStatus == null){
            log.warn("Attempted to set null arming status. Ignoring.");
            return;
        }
        ArmingStatus previousArmingStatus = getArmingStatus();
        if(armingStatus == ArmingStatus.DISARMED){
            setAlarmStatus(AlarmStatus.NO_ALARM);
            securityRepository.setArmingStatus(armingStatus);
            log.info("System status set to DISARMED");
            return;
        }
        Set<Sensor> currentSensors = getSensors();
        log.info("Arming system to {}. Resetting sensors.", armingStatus);
        for (Sensor sensor : Set.copyOf(currentSensors)) {
            if (sensor != null && Boolean.TRUE.equals(sensor.getActive())){
                changeSensorActivationStatus(sensor, false);
                log.debug("Deactivated sensor '{}' during arming.", sensor.getName());
            }
        }
        securityRepository.setArmingStatus(armingStatus);
        log.info("System status set to {}", armingStatus);
        if (armingStatus == ArmingStatus.ARMED_HOME && this.catDetectedCurrently){
            log.info("Cat detected flag is true while arming home, setting alarm.");
            setAlarmStatus(AlarmStatus.ALARM);
        }
    }
    public synchronized boolean isCatCurrentlyDetected() {
        return this.catDetectedCurrently;
    }
    private synchronized void handleCatDetectionLogic(boolean cat) {
        this.catDetectedCurrently = cat;
        log.debug("Cat detection status updated to: {}", cat);
        ArmingStatus currentArmingStatus = getArmingStatus();
        if (cat && currentArmingStatus == ArmingStatus.ARMED_HOME) {
            log.info("Cat detected while armed home, setting alarm.");
            setAlarmStatus(AlarmStatus.ALARM);
        }
        else if (!cat){
            if (areAllSensorsInactive()){
                log.info("No cat detected and sensors inactive, setting to no alarm.");
                setAlarmStatus(AlarmStatus.NO_ALARM);
            } else {
                log.debug("No cat detected, but sensors are active. Alarm status unchanged by image.");
            }
        }
        statusListeners.forEach(sl -> sl.catDetected(cat));
    }
    public void addStatusListener(StatusListener statusListener){
        if (statusListener != null){
            statusListeners.add(statusListener);
            log.debug("Added status listener: {}", statusListener.getClass().getSimpleName());
        }
    }
    public void removeStatusListener(StatusListener statusListener){
        if (statusListener != null) {
            if (statusListeners.remove(statusListener)) {
                log.debug("Removed status listener: {}", statusListener.getClass().getSimpleName());
            }
        }
    }
    public synchronized void setAlarmStatus(AlarmStatus status) {
        if (status == null) {
            log.warn("Attempted to set null alarm status. Ignoring.");
            return;
        }
        ArmingStatus currentArmingStatus = securityRepository.getArmingStatus();
        if (currentArmingStatus == null) {
            log.error("ArmingStatus from repository is null, cannot reliably set alarm status.");
            if (status == AlarmStatus.PENDING_ALARM || status == AlarmStatus.ALARM) {
                log.warn("Preventing setting PENDING/ALARM because ArmingStatus is null.");
                return;
            }
        }
        else {
            if ((status == AlarmStatus.PENDING_ALARM || status == AlarmStatus.ALARM) && currentArmingStatus == ArmingStatus.DISARMED) {
                log.debug("Ignoring {} status change while system is DISARMED.", status);
                return;
            }
        }
        AlarmStatus currentRepoAlarmStatus = securityRepository.getAlarmStatus();
        if (currentRepoAlarmStatus != status) {
            log.info("Changing alarm status from {} to {}", currentRepoAlarmStatus, status);
            securityRepository.setAlarmStatus(status);
            statusListeners.forEach(sl -> sl.notify(status));
        } else {
            log.trace("Alarm status already {}, no change needed.", status);
        }
    }
    private synchronized void handleSensorActivated(){
        ArmingStatus armingStatus = getArmingStatus();
        if (armingStatus == ArmingStatus.DISARMED) {
            log.debug("Sensor activated while DISARMED. No status change.");
            return;
        }
        AlarmStatus currentAlarmStatus = getAlarmStatus();
        if (currentAlarmStatus == null) {
            log.error("Cannot handle sensor activation: currentAlarmStatus is null.");
            return;
        }
        switch (currentAlarmStatus){
            case NO_ALARM:
                log.info("Sensor activated while ARMED/NO_ALARM. Setting PENDING_ALARM.");
                setAlarmStatus(AlarmStatus.PENDING_ALARM);
                break;
            case PENDING_ALARM:
                log.info("Sensor activated while PENDING_ALARM. Setting ALARM.");
                setAlarmStatus(AlarmStatus.ALARM);
                break;
            case ALARM:
                log.debug("Sensor activated while ALARM. No status change.");
                break;
        }
    }
    private synchronized void handleSensorDeactivated(){
        AlarmStatus currentAlarmStatus = getAlarmStatus();
        if (currentAlarmStatus == null) {
            log.error("Cannot handle sensor deactivation: currentAlarmStatus is null.");
            return;
        }
        if (currentAlarmStatus == AlarmStatus.ALARM){
            log.debug("Sensor deactivated while ALARM. No status change.");
            return;
        }
        if (currentAlarmStatus == AlarmStatus.PENDING_ALARM){
            if (areAllSensorsInactive()) {
                log.info("Sensor deactivated while PENDING_ALARM, and all sensors now inactive. Setting NO_ALARM.");
                setAlarmStatus(AlarmStatus.NO_ALARM);
            } else {
                log.debug("Sensor deactivated while PENDING_ALARM, but other sensors still active.");
            }
        }
        else if (currentAlarmStatus == AlarmStatus.NO_ALARM){
            log.debug("Sensor deactivated while NO_ALARM. No status change.");
        }
    }
    private synchronized boolean areAllSensorsInactive(){
        Set<Sensor> sensors = getSensors();
        if (sensors == null){
            log.warn("Sensor set from repository is null. Assuming sensors are inactive for safety.");
            return true;
        }
        return sensors.stream().noneMatch(sensor -> sensor != null && Boolean.TRUE.equals(sensor.getActive()));
    }
    public synchronized void changeSensorActivationStatus(Sensor sensor, boolean active) {
        if (sensor == null){
            log.warn("Attempted to change activation status for a null sensor. Ignoring.");
            return;
        }
        Boolean currentSensorActiveState = sensor.getActive();
        boolean currentStateIsTrue = Boolean.TRUE.equals(currentSensorActiveState);
        if (currentStateIsTrue == active) {
            log.trace("Sensor {} activation status ({}) already matches target state {}. No change needed.", sensor.getName(), currentSensorActiveState, active);
            return;
        }
        log.info("Changing sensor '{}' (ID: {}) activation status from {} to {}",
                sensor.getName(), sensor.getSensorId(), currentSensorActiveState, active);
        sensor.setActive(active);
        securityRepository.updateSensor(sensor);

        if (active){
            handleSensorActivated();
        } else {
            handleSensorDeactivated();
        }
        statusListeners.forEach(StatusListener::sensorStatusChanged);
    }
    public void processImage(BufferedImage currentCameraImage){
        try {
            boolean catDetected = false;
            if (currentCameraImage != null) {
                if (this.imageService == null) {
                    log.error("ImageService is null. Cannot process image.");
                    handleCatDetectionLogic(false);
                    return;
                }
                catDetected = imageService.imageContainsCat(currentCameraImage, 50.0f);
                log.debug("Image processed. Cat detected: {}", catDetected);
            } else{
                log.debug("processImage called with null image, assuming no cat.");
                catDetected = false;
            }
            handleCatDetectionLogic(catDetected);
        } catch (Exception e) {
            log.error("Error processing image: {}. Assuming no cat detected.", e.getMessage(), e);
            handleCatDetectionLogic(false);
        }
    }
    public synchronized AlarmStatus getAlarmStatus() {
        return securityRepository.getAlarmStatus();
    }

    public synchronized ArmingStatus getArmingStatus() {
        return securityRepository.getArmingStatus();
    }

    public synchronized Set<Sensor> getSensors(){
        Set<Sensor> sensors = securityRepository.getSensors();
        return (sensors != null) ? Set.copyOf(sensors) : Set.of();
    }
    public synchronized void addSensor(Sensor sensor){
        if (sensor != null) {
            log.info("Adding sensor: {} (ID: {})", sensor.getName(), sensor.getSensorId());
            securityRepository.addSensor(sensor);
            statusListeners.forEach(StatusListener::sensorStatusChanged);
        } else {
            log.warn("Attempted to add null sensor.");
        }
    }
    public synchronized void removeSensor(Sensor sensor){
        if (sensor != null){
            log.info("Removing sensor: {} (ID: {})", sensor.getName(), sensor.getSensorId());
            securityRepository.removeSensor(sensor);
            handleSensorDeactivated();
            statusListeners.forEach(StatusListener::sensorStatusChanged);
        }
        else{log.warn("Attempted to remove null sensor.");}
    }
}