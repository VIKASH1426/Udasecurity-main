package com.udacity.catpoint;

import com.udacity.catpoint.application.StatusListener;
import com.udacity.catpoint.data.*;
import com.udacity.catpoint.image.ImageService;
import com.udacity.catpoint.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecurityServiceTest {

    private SecurityService securityService;

    @Mock private SecurityRepository securityRepository;
    @Mock private ImageService imageService;
    @Mock private StatusListener statusListener;
    @Mock private StatusListener statusListener1;
    @Mock private StatusListener statusListener2;

    @BeforeEach
    void init() {
        securityService = new SecurityService(securityRepository, imageService);
    }

    // --- Arming Status & Sensor Activation ---

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void armedSystemAndSensorActivated_setsPeningAlarm(ArmingStatus armingStatus) {
        Sensor sensor = new Sensor("Test Sensor", SensorType.DOOR);
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
    }

    @ParameterizedTest
    @EnumSource(value = ArmingStatus.class, names = {"ARMED_HOME", "ARMED_AWAY"})
    void pendingAlarmAndSensorActivated_sesAlarm(ArmingStatus armingStatus) {
        Sensor sensor = new Sensor("Test Sensor", SensorType.DOOR);
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void sensorDeactivatedWhileInactive_doesNotChngeAlarm() {
        Sensor sensor = new Sensor("Test Sensor", SensorType.DOOR);
        sensor.setActive(false);

        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test
    void reactivatingSensorInPendingAlarm_setsAlrm() {
        Sensor sensor = new Sensor("Sensor A", SensorType.WINDOW);
        sensor.setActive(true);

        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void activeAlarmState_doesNotChangeOnSenorToggle() {
        Sensor sensor = new Sensor("Sensor X", SensorType.MOTION);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test
    void sensorDeactivationInPendingAlarm_withNoctiveSensors_setsNoAlarm() {
        Sensor sensor = new Sensor("Test", SensorType.DOOR);
        sensor.setActive(true);

        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(new HashSet<>());

        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    // --- Image Processing Tests ---

    @Test
    void armedHomeAndCatDetected_stsAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.processImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void catDetectedWhenArmedAway_doesNotSetAlrm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.processImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        verify(securityRepository, never()).setAlarmStatus(AlarmStatus.ALARM);
    }

    @Test
    void noCatAndAllSensorsInactive_setsNolarm() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        when(securityRepository.getSensors()).thenReturn(new HashSet<>());

        securityService.processImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void processImage_withNullImage_doesNoCrashOrSetAlarm() {
        securityService.processImage(null);
        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test
    void setArmingHomeWithCatDetected_setsAlarm() {
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);

        securityService.processImage(mock(BufferedImage.class));
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }

    // --- Arming/Disarming Tests ---

    @Test
    void systemDisarmed_setsNAlarm() {
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void armingSystem_deactivatesAllSsors() {
        Sensor sensor1 = new Sensor("S1", SensorType.DOOR);
        Sensor sensor2 = new Sensor("S2", SensorType.WINDOW);
        sensor1.setActive(true);
        sensor2.setActive(true);

        when(securityRepository.getSensors()).thenReturn(Set.of(sensor1, sensor2));

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        assertFalse(sensor1.getActive());
        assertFalse(sensor2.getActive());

        verify(securityRepository).updateSensor(sensor1);
        verify(securityRepository).updateSensor(sensor2);
    }

    @Test
    void armingSystem_resetsAllSensorsRegardleOfStatus() {
        Sensor s1 = new Sensor("S1", SensorType.DOOR);
        Sensor s2 = new Sensor("S2", SensorType.WINDOW);
        Sensor s3 = new Sensor("S3", SensorType.MOTION);
        s1.setActive(true);
        s2.setActive(true);

        when(securityRepository.getSensors()).thenReturn(Set.of(s1, s2, s3));

        doAnswer(invocation -> {
            Sensor sensor = invocation.getArgument(0);
            sensor.setActive(false);
            return null;
        }).when(securityRepository).updateSensor(any(Sensor.class));

        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);

        assertFalse(s1.getActive());
        assertFalse(s2.getActive());
        assertFalse(s3.getActive());

        verify(securityRepository).updateSensor(s1);
        verify(securityRepository).updateSensor(s2);
        verify(securityRepository).updateSensor(s3);
    }

    // --- Status Listeners ---

    @Test
    void addingAndRemovingStatusListeners_behaveCorrectly() {
        securityService.addStatusListener(statusListener1);
        securityService.addStatusListener(statusListener2);

        securityService.removeStatusListener(statusListener1);

        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));

        verify(statusListener1, never()).catDetected(anyBoolean());
        verify(statusListener2).catDetected(true);
    }

    @Test
    void removedStatusListener_doesNotReceiveNotfications() {
        securityService.addStatusListener(statusListener);
        securityService.removeStatusListener(statusListener);

        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));

        verify(statusListener, never()).catDetected(anyBoolean());
    }

    @Test
    void notifyStatusListener_onCatDeection() {
        securityService.addStatusListener(statusListener);

        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        securityService.processImage(mock(BufferedImage.class));

        verify(statusListener).catDetected(true);
    }

    // --- Sensor Add/Remove ---

    @Test
    void addSensor_delegatesTRepository() {
        Sensor sensor = new Sensor("S1", SensorType.DOOR);
        securityService.addSensor(sensor);
        verify(securityRepository).addSensor(sensor);
    }

    @Test
    void removeSensor_delegatesToRepsitory() {
        Sensor sensor = new Sensor("S1", SensorType.DOOR);
        securityService.removeSensor(sensor);
        verify(securityRepository).removeSensor(sensor);
    }

    // --- Alarm Logic w/ Sensors & Image ---

    @Test
    void noCatAndAllSensorsInactiveDuringPending_setsNAlarm() {
        Sensor sensor1 = new Sensor("Door", SensorType.DOOR);
        Sensor sensor2 = new Sensor("Window", SensorType.WINDOW);
        sensor1.setActive(false);
        sensor2.setActive(false);

        when(securityRepository.getSensors()).thenReturn(Set.of(sensor1, sensor2));
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);

        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        securityService.processImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void pendingAlarmAndAllSensorsInactive_setsNAlarm() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(new HashSet<>());

        securityService.checkSensorsAndUpdateStatus();

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }
    @Test
    void armingSystem_makesAllSensorsInctive() {
        Sensor sensor1 = new Sensor("Front Door", SensorType.DOOR);
        Sensor sensor2 = new Sensor("Back Window", SensorType.WINDOW);
        sensor1.setActive(true);
        sensor2.setActive(true);

        Set<Sensor> sensors = Set.of(sensor1, sensor2);
        when(securityRepository.getSensors()).thenReturn(sensors);

        securityService.setArmingStatus(ArmingStatus.ARMED_AWAY);

        assertFalse(sensor1.getActive());
        assertFalse(sensor2.getActive());

        verify(securityRepository).updateSensor(sensor1);
        verify(securityRepository).updateSensor(sensor2);
    }

    @Test
    void noCatDetectedAndAllSensorsInactive_setsNAlarm() {
        Sensor sensor1 = new Sensor("Window", SensorType.WINDOW);
        Sensor sensor2 = new Sensor("Door", SensorType.DOOR);
        sensor1.setActive(false);
        sensor2.setActive(false);

        Set<Sensor> sensors = Set.of(sensor1, sensor2);
        when(securityRepository.getSensors()).thenReturn(sensors);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);

        securityService.processImage(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB));

        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
    }

    @Test
    void activeAlarm_sensorActivationDoesNtAffectAlarm() {
        Sensor sensor = new Sensor("Motion", SensorType.MOTION);
        sensor.setActive(false);

        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        //when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);

        securityService.changeSensorActivationStatus(sensor, true);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

    @Test
    void activeAlarm_sensorDeactivationDoesNtAffectAlarm() {
        Sensor sensor = new Sensor("Window", SensorType.WINDOW);
        sensor.setActive(true);

        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);

        securityService.changeSensorActivationStatus(sensor, false);

        verify(securityRepository, never()).setAlarmStatus(any());
    }

}
