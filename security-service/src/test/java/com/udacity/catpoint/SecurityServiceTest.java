package com.udacity.catpoint;

import com.udacity.catpoint.service.*;
import com.udacity.catpoint.image.ImageService;
import com.udacity.catpoint.image.FakeImageService;
import com.udacity.catpoint.application.StatusListener;
import com.udacity.catpoint.data.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
//Unit tests for the SecurityService//
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SecurityServiceTest {
    @InjectMocks
    private SecurityService securityService;
    @Mock
    private SecurityRepository securityRepository;
    @Mock
    private FakeImageService imageService;
    @Mock
    private StatusListener mockListener;
    private Sensor sensor;
    private static final BufferedImage TEST_IMAGE = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
    private Sensor createSensor(boolean active) {
        Sensor s = new Sensor("Sensor_" + UUID.randomUUID(), SensorType.DOOR);
        s.setActive(active);
        return s;
    }
    private Set<Sensor> createSensors(int count, boolean active) {
        return IntStream.range(0, count)
                .mapToObj(i -> createSensor(active))
                .collect(Collectors.toSet());
    }
    private static Stream<ArmingStatus> armedStatusProvider() {
        return Stream.of(ArmingStatus.ARMED_HOME, ArmingStatus.ARMED_AWAY);
    }
    @BeforeEach
    void setUp() {
        sensor = createSensor(false);
        securityService.addStatusListener(mockListener);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
    }
    @ParameterizedTest
    @MethodSource("armedStatusProvider")
    @DisplayName("Req 1: Activate Sensor When Armed/NoAlarm -> Pending")
    void changeSensor_activateSensorWhenArmedAndNoAlarm_setsStatusPending(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.PENDING_ALARM);
        verify(mockListener).notify(AlarmStatus.PENDING_ALARM);
    }
    @ParameterizedTest
    @MethodSource("armedStatusProvider")
    @DisplayName("Req 2/5: Activate Sensor When Armed/Pending -> Alarm")
    void changeSensor_activateSensorWhenArmedAndPending_setsStatusAlarm(ArmingStatus armingStatus) {
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
        verify(mockListener).notify(AlarmStatus.ALARM);
    }

    @Test
    @DisplayName("Req 3: Deactivate Sensor When Pending -> No Alarm (If All Inactive)")
    void changeSensor_deactivateSensorWhenPendingAndBecomesInactive_setsStatusNoAlarm() {
        sensor.setActive(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        when(securityRepository.getSensors()).thenReturn(Set.of(sensor));
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).updateSensor(argThat(s -> s.getSensorId().equals(sensor.getSensorId()) && !s.getActive()));
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(mockListener).notify(AlarmStatus.NO_ALARM);
    }

    @Test
    @DisplayName("Req 3 Edge: Deactivate One Sensor When Pending -> Stays Pending (If Others Active)")
    void changeSensor_deactivateOneSensorWhenPndingButOthersActive_staysPending() {
        Sensor sensorToDeactivate = createSensor(true);
        Sensor stillActiveSensor = createSensor(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        when(securityRepository.getSensors()).thenReturn(Set.of(stillActiveSensor));
        securityService.changeSensorActivationStatus(sensorToDeactivate, false);
        verify(securityRepository).updateSensor(argThat(s -> s.getSensorId().equals(sensorToDeactivate.getSensorId()) && !s.getActive()));
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(mockListener, never()).notify(any(AlarmStatus.class));
    }
    @ParameterizedTest
    @MethodSource("armedStatusProvider")
    @DisplayName("Req 4: Sensor Change When Alarm Active -> No Alarm Change")
    void changeSensor_changeSensorStateWhenAarmActive_noChangeInAlarmState(ArmingStatus armingStatus) {
        Sensor sensorActive = createSensor(true);
        Sensor sensorInactive = createSensor(false);
        when(securityRepository.getArmingStatus()).thenReturn(armingStatus);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.changeSensorActivationStatus(sensorInactive, true);
        securityService.changeSensorActivationStatus(sensorActive, false);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(mockListener, never()).notify(any(AlarmStatus.class));
        verify(securityRepository).updateSensor(sensorInactive);
        verify(securityRepository).updateSensor(sensorActive);
    }
    @Test
    @DisplayName("Req 6: Deactivate Sensor When Already Inactive -> No Change")
    void changeSensr_deactivateSensorWhenAleadyInactive_noChange() {
        sensor.setActive(false);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository, never()).updateSensor(any(Sensor.class));
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(mockListener, never()).notify(any(AlarmStatus.class));
        verify(mockListener, never()).sensorStatusChanged();
    }
    @Test
    @DisplayName("Activate Sensor When Already Active -> No Change")
    void changeSensor_actvateSensorWhenlreadyActive_noChange() {
        sensor.setActive(true);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository, never()).updateSensor(any(Sensor.class));
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(mockListener, never()).notify(any(AlarmStatus.class));
        verify(mockListener, never()).sensorStatusChanged();
    }
    @Test
    @DisplayName("Req 7/11: Cat Detected When Armed Home -> Alarm")
    void processImagecatDetecedAndArmedHome_setsStatusAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
        securityService.processImage(TEST_IMAGE);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
        verify(mockListener).notify(AlarmStatus.ALARM);
    }
    @Test
    @DisplayName("Req 8 [Case: NO_ALARM start]: No Cat & Sensors Inactive -> Stays No Alarm")
    void processImage_noCt_sensorsInactive_initialNAlarm_staysNoAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getSensors()).thenReturn(createSensors(2, false));
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        securityService.processImage(TEST_IMAGE);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(mockListener, never()).notify(any(AlarmStatus.class));
    }
    @Test
    @DisplayName("Req 8 [Case: PENDING_ALARM start]: No Cat & Sensors Inactive -> Sets No Alarm & Notifies Listener")
    void processImage_noCat_sesorsInactive_nitialPendingAlarm_setsNoAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getSensors()).thenReturn(createSensors(2, false));
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        securityService.processImage(TEST_IMAGE);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(mockListener).notify(AlarmStatus.NO_ALARM);
    }
    @Test
    @DisplayName("Req 8 Edge: No Cat & Sensors Active -> No Change (If ALARM)")
    void processImage_noCatDetectedAnSensorsActive_nChangeIfAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getSensors()).thenReturn(createSensors(1, true));
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(false);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.ALARM);
        securityService.processImage(TEST_IMAGE);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(mockListener, never()).notify(any(AlarmStatus.class));
    }
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"ALARM", "PENDING_ALARM"})
    @DisplayName("Req 9: Disarm System -> No Alarm")
    void setArming_disarmSystem_setsSttusNoAlarm(AlarmStatus initialStatus) {
        when(securityRepository.getAlarmStatus()).thenReturn(initialStatus);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        securityService.setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setArmingStatus(ArmingStatus.DISARMED);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(mockListener).notify(AlarmStatus.NO_ALARM);
    }
    @ParameterizedTest
    @MethodSource("armedStatusProvider")
    @DisplayName("Req 10: Arm System -> Resets Active Sensors")
    void setArming_armSystem_resetsAlActiveSensorsToInactive(ArmingStatus armingStatus) {
        Set<Sensor> sensors = new HashSet<>();
        Sensor active1 = createSensor(true);
        Sensor active2 = createSensor(true);
        Sensor inactive1 = createSensor(false);
        sensors.add(active1);
        sensors.add(active2);
        sensors.add(inactive1);
        when(securityRepository.getSensors()).thenReturn(sensors);
        securityService.setArmingStatus(armingStatus);
        verify(securityRepository).updateSensor(argThat(s -> s.getSensorId().equals(active1.getSensorId()) && !s.getActive()));
        verify(securityRepository).updateSensor(argThat(s -> s.getSensorId().equals(active2.getSensorId()) && !s.getActive()));
        verify(securityRepository, never()).updateSensor(argThat(s -> s.getSensorId().equals(inactive1.getSensorId())));
        verify(securityRepository).setArmingStatus(armingStatus);
        verify(mockListener, times(2)).sensorStatusChanged();
    }
    @Test
    @DisplayName("Req 11: Arm Home When Cat Already Detected -> Alarm")
    void setArming_armHomeWhenCatDetcted_setsStatusAlarm() {
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat())).thenReturn(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.DISARMED);
        when(securityRepository.getSensors()).thenReturn(createSensors(0, false));
        securityService.processImage(TEST_IMAGE);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(mockListener).notify(AlarmStatus.ALARM);
        verify(securityRepository).setArmingStatus(ArmingStatus.ARMED_HOME);
        verify(securityRepository).setAlarmStatus(AlarmStatus.ALARM);
    }
    @Test
    @DisplayName("Add Sensor - Delegates to Repository")
    void addSensor_delegatesToReository() {
        securityService.addSensor(sensor);
        verify(securityRepository).addSensor(sensor);
        verify(mockListener).sensorStatusChanged();
    }
    @Test
    @DisplayName("Remove Sensor - Delegates to Repository and Updates State")
    void removeSensor_delegaesToRepository() {
        sensor.setActive(true);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        when(securityRepository.getSensors()).thenReturn(Set.of());
        securityService.removeSensor(sensor);
        verify(securityRepository).removeSensor(sensor);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(mockListener).notify(AlarmStatus.NO_ALARM);
        verify(mockListener).sensorStatusChanged();
    }
    @Test
    @DisplayName("Constructor: Null SecurityRepository -> Throws Exception")
    void constructor_nullRepositorythrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SecurityService((SecurityRepository) null, imageService);
        });
    }
    @Test
    @DisplayName("Constructor Test: Null ImageService -> Throws IllegalArgumentException")
    void constructor_nullImageService_thrsException(){
        assertThrows(IllegalArgumentException.class, () -> {
            new SecurityService(securityRepository, null);
        });
        verifyNoInteractions(mockListener);
    }
    @Test
    @DisplayName("Set Arming Status with Null -> No Change")
    void setArmiStatus_nullStatus_noAction(){
        securityService.setArmingStatus(null);
        verify(securityRepository, never()).setArmingStatus(any());
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
    }
    @Test
    @DisplayName("Remove Null Status Listener -> No Action")
    void removeStatusListenr_nullListener_noAction() {
        securityService.removeStatusListener(null);
        assertTrue(true, "Executing removeStatusListener(null) should not throw");
        verify(mockListener, never()).notify(any());
    }
    @Test
    @DisplayName("Remove Status Listener Not Present -> No Error, Original Listener Remains")
    void removeStatusListener_listenertPresent_noError() {
        StatusListener anotherListener =mock(StatusListener.class);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        securityService.removeStatusListener(anotherListener);
        securityService.setAlarmStatus(AlarmStatus.ALARM);
        verify(mockListener).notify(AlarmStatus.ALARM);
        verify(anotherListener, never()).notify(any());
    }
    @Test
    @DisplayName("Set Alarm Status with Null -> No Change")
    void setAlarmStatus_nullStatus_notion(){
        securityService.setAlarmStatus(null);
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
    }
    @ParameterizedTest
    @EnumSource(value = AlarmStatus.class, names = {"PENDING_ALARM", "ALARM"})
    @DisplayName("Set Alarm Status to Pending/Alarm While Disarmed -> No Change")
    void setAlarmStatus_pendingOrAlarmWheisarmed_noAction(AlarmStatus targetStatus) {
        securityService.setAlarmStatus(targetStatus);
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
    }
    @Test
    @DisplayName("Set Alarm Status When Arming Status is Null -> Prevents Pending/Alarm")
    void setAlarmStatus_nullArmingStatus_prevesPendingAlarm() {
        when(securityRepository.getArmingStatus()).thenReturn(null);
        securityService.setAlarmStatus(AlarmStatus.PENDING_ALARM);
        securityService.setAlarmStatus(AlarmStatus.ALARM);
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
    }
    @Test
    @DisplayName("Set Alarm Status When Already Set -> No Change")
    void setAlarmStatus_alreadySet_noAcon(){
        AlarmStatus initialStatus = AlarmStatus.PENDING_ALARM;
        when(securityRepository.getAlarmStatus()).thenReturn(initialStatus);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        reset(securityRepository, mockListener);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(initialStatus);
        securityService.setAlarmStatus(initialStatus);
        verify(securityRepository, never()).setAlarmStatus(any(AlarmStatus.class));
        verify(mockListener, never()).notify(any(AlarmStatus.class));
    }
    @Test
    @DisplayName("Deactivate Sensor When No Alarm -> No Alarm Change")
    void handleSensorDeactivated_whenNoArm_noChange(){
        sensor.setActive(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).updateSensor(sensor);
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
        verify(mockListener).sensorStatusChanged();
    }
    @Test
    @DisplayName("Activate Sensor When Disarmed -> No Alarm Change")
    void handleSensorActivated_whenDisarmenoChange() {
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).updateSensor(sensor);
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
        verify(mockListener).sensorStatusChanged();
    }
    @Test
    @DisplayName("Handle Sensor Activation When Repo Returns Null Alarm Status -> No Change")
    void handleSensorActivated_nullAlarmatus_noChange(){
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        when(securityRepository.getAlarmStatus()).thenReturn(null);
        securityService.changeSensorActivationStatus(sensor, true);
        verify(securityRepository).updateSensor(sensor);
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
        verify(mockListener).sensorStatusChanged();
    }
    @Test
    @DisplayName("Handle Sensor Deactivation When Repo Returns Null Alarm Status -> No Change")
    void handleSensorDeactivated_nullAlamStatus_noChange(){
        sensor.setActive(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        when(securityRepository.getAlarmStatus()).thenReturn(null);
        securityService.changeSensorActivationStatus(sensor, false);
        verify(securityRepository).updateSensor(sensor);
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
        verify(mockListener).sensorStatusChanged();
    }
    @Test
    @DisplayName("Check All Sensors Inactive When Repo Returns Null Set -> Returns True")
    void areAllSensorsInactive_nullSetFromepo_returnsTrue() {
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_AWAY);
        when(securityRepository.getSensors()).thenReturn(null);
        Sensor sensorToDeactivate = createSensor(true);
        securityService.changeSensorActivationStatus(sensorToDeactivate, false);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(mockListener).notify(AlarmStatus.NO_ALARM);
    }
    @Test
    @DisplayName("Test isCatCurrentlyDetected Getter")
    void isCatCurrentlyDetected_returnsCurentFlagState(){
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(true);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        securityService.processImage(TEST_IMAGE);
        assertTrue(securityService.isCatCurrentlyDetected(), "Should return true after cat detected");
        reset(securityRepository);
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.NO_ALARM);
        when(imageService.imageContainsCat(any(), anyFloat())).thenReturn(false);
        securityService.processImage(TEST_IMAGE);
        assertFalse(securityService.isCatCurrentlyDetected(), "Should return false after no cat detected");
    }
    @Test
    @DisplayName("Change Sensor Status with Null Sensor -> No Change")
    void changeSensorActivationStatus_nullSenor_noAction() {
        securityService.changeSensorActivationStatus(null, true);
        verify(securityRepository, never()).updateSensor(any());
        verify(securityRepository, never()).setAlarmStatus(any());
        verify(mockListener, never()).notify(any());
        verify(mockListener, never()).sensorStatusChanged();
    }
    @Test
    @DisplayName("Process Image When ImageService Throws Exception -> Assumes No Cat")
    void processImage_exceptionFromImageService_ssumesNoCat() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(createSensors(0, false));
        when(imageService.imageContainsCat(any(BufferedImage.class), anyFloat()))
                .thenThrow(new RuntimeException("Simulated image processing error"));
        securityService.processImage(TEST_IMAGE);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(mockListener).notify(AlarmStatus.NO_ALARM);
        verify(mockListener).catDetected(false);
    }

    @Test
    @DisplayName("Process Image with Null Image -> Assumes No Cat")
    void processImage_nullImage_assumesNCat() {
        when(securityRepository.getArmingStatus()).thenReturn(ArmingStatus.ARMED_HOME);
        when(securityRepository.getAlarmStatus()).thenReturn(AlarmStatus.PENDING_ALARM);
        when(securityRepository.getSensors()).thenReturn(createSensors(0, false));
        securityService.processImage(null);
        verify(securityRepository).setAlarmStatus(AlarmStatus.NO_ALARM);
        verify(mockListener).notify(AlarmStatus.NO_ALARM);
        verify(mockListener).catDetected(false);
    }
    @Test
    @DisplayName("Add Null Sensor -> No Action")
    void addSensor_nullSensor_noction() {
        securityService.addSensor(null);
        verify(securityRepository, never()).addSensor(any());
        verify(mockListener, never()).sensorStatusChanged();
    }
    @Test
    @DisplayName("Remove Null Sensor -> No Action")
    void removeSensor_nullSensor_noAtion() {
        securityService.removeSensor(null);
        verify(securityRepository, never()).removeSensor(any());
        verify(mockListener, never()).sensorStatusChanged();
        verify(mockListener, never()).notify(any());
    }
}