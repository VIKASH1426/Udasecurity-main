package com.udacity.catpoint.application;
import com.udacity.catpoint.data.Sensor;
import com.udacity.catpoint.data.SensorType;
import com.udacity.catpoint.service.SecurityService;
import com.udacity.catpoint.service.StyleService;
import net.miginfocom.swing.MigLayout;
import javax.swing.*;
import java.io.Serial;
public class SensorPanel extends JPanel implements java.io.Serializable{
    @Serial
    private static final long serialVersionUID = 1L;
    private final SecurityService securityService;
    private final transient JLabel panelLabel = new JLabel("Sensor Management");
    private final transient JLabel newSensorName = new JLabel("Name:");
    private final transient JLabel newSensorType = new JLabel("Sensor Type:");
    private final transient JTextField newSensorNameField = new JTextField();
    private final transient JComboBox<SensorType> newSensorTypeDropdown = new JComboBox<>(SensorType.values());
    private final transient JButton addNewSensorButton = new JButton("Add New Sensor");
    private final transient JPanel sensorListPanel;
    private final transient JPanel newSensorPanel;

    public SensorPanel(SecurityService securityService){
        super();
        setLayout(new MigLayout());
        if (securityService == null) {
            throw new IllegalArgumentException("SecurityService cannot be null");
        }
        this.securityService = securityService;

        panelLabel.setFont(StyleService.HEADING_FONT);
        addNewSensorButton.addActionListener(e -> {
            String name = newSensorNameField.getText();
            Object selectedType = newSensorTypeDropdown.getSelectedItem();
            if (name != null && !name.trim().isEmpty() && selectedType instanceof SensorType) {
                addSensor(new Sensor(name.trim(), (SensorType)selectedType));
            } else{
                JOptionPane.showMessageDialog(this, "Please enter a valid sensor name.", "Input Error", JOptionPane.WARNING_MESSAGE);
            }
        });
        newSensorPanel = buildAddSensorPanel();
        sensorListPanel = new JPanel();
        sensorListPanel.setLayout(new MigLayout());

        updateSensorList(sensorListPanel);

        add(panelLabel, "wrap");
        add(newSensorPanel, "span");
        add(sensorListPanel, "span");
    }
    private JPanel buildAddSensorPanel(){
        JPanel p = new JPanel();
        p.setLayout(new MigLayout());
        p.add(newSensorName);
        p.add(newSensorNameField, "width 50:100:200");
        p.add(newSensorType);
        p.add(newSensorTypeDropdown, "wrap");
        p.add(addNewSensorButton, "span 3");
        return p;
    }
    private void updateSensorList(JPanel p){
        SwingUtilities.invokeLater(() -> {
            p.removeAll();
            this.securityService.getSensors().stream().sorted().forEach(s -> {
                JLabel sensorLabel = new JLabel(String.format("%s(%s): %s", s.getName(), s.getSensorType().toString(), (s.getActive() ? "Active" : "Inactive")));
                JButton sensorToggleButton = new JButton((s.getActive() ? "Deactivate" : "Activate"));
                JButton sensorRemoveButton = new JButton("Remove Sensor");
                sensorToggleButton.addActionListener(e -> setSensorActivity(s, !s.getActive()));
                sensorRemoveButton.addActionListener(e -> removeSensor(s));
                p.add(sensorLabel, "width 300:300:300");
                p.add(sensorToggleButton, "width 100:100:100");
                p.add(sensorRemoveButton, "wrap");
            });
            p.revalidate();
            p.repaint();
        });
    }
    private void setSensorActivity(Sensor sensor, Boolean isActive) {
        this.securityService.changeSensorActivationStatus(sensor, isActive);
        updateSensorList(sensorListPanel);
    }
    private void addSensor(Sensor sensor){
        if(this.securityService.getSensors().size() < 4) {
            this.securityService.addSensor(sensor);
            updateSensorList(sensorListPanel);
        } else{
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(this, "To add more than 4 sensors, please subscribe to our Premium Membership!", "Sensor Limit Reached", JOptionPane.INFORMATION_MESSAGE)
            );
        }
    }
    private void removeSensor(Sensor sensor){
        this.securityService.removeSensor(sensor);
        updateSensorList(sensorListPanel);
    }
}