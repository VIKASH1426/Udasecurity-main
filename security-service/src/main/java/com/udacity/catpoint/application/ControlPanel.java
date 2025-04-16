package com.udacity.catpoint.application;
import com.udacity.catpoint.data.ArmingStatus;
import com.udacity.catpoint.service.SecurityService;
import com.udacity.catpoint.service.StyleService;
import net.miginfocom.swing.MigLayout;
import javax.swing.*;
import java.io.Serial;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
public class ControlPanel extends JPanel implements java.io.Serializable{
    @Serial
    private static final long serialVersionUID = 1L;
    private final SecurityService securityService;
    private transient Map<ArmingStatus, JButton> buttonMap;

    public ControlPanel(SecurityService securityService){
        super();
        setLayout(new MigLayout());
        if (securityService == null) {
            throw new IllegalArgumentException("SecurityService cannot be null");
        }
        this.securityService= securityService;
        JLabel panelLabel=new JLabel("System Control");
        panelLabel.setFont(StyleService.HEADING_FONT);
        add(panelLabel, "span 3, wrap");
        buttonMap = Arrays.stream(ArmingStatus.values())
                .collect(Collectors.toMap(status -> status, status -> new JButton(status.getDescription())));
        buttonMap.forEach((k, v) -> {
            v.addActionListener(e -> {
                this.securityService.setArmingStatus(k);
                buttonMap.forEach((status, button) -> button.setBackground(status == k ? status.getColor() : null));
            });
        });
        Arrays.stream(ArmingStatus.values()).forEach(status -> add(buttonMap.get(status)));
        ArmingStatus currentStatus = this.securityService.getArmingStatus();
        buttonMap.get(currentStatus).setBackground(currentStatus.getColor());
    }
}