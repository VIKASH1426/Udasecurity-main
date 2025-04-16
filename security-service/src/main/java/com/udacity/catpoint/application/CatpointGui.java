package com.udacity.catpoint.application;
import com.udacity.catpoint.image.FakeImageService;
import com.udacity.catpoint.image.ImageService;
import com.udacity.catpoint.data.PretendDatabaseSecurityRepositoryImpl;
import com.udacity.catpoint.data.SecurityRepository;
import com.udacity.catpoint.service.SecurityService;
import net.miginfocom.swing.MigLayout;
import javax.swing.*;
import java.io.Serial;
public class CatpointGui extends JFrame implements java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final transient SecurityRepository securityRepository = new PretendDatabaseSecurityRepositoryImpl();
    private final transient ImageService imageService = new FakeImageService();
    private final transient SecurityService securityService = new SecurityService(securityRepository, (FakeImageService) imageService);
    private final transient DisplayPanel displayPanel = new DisplayPanel(securityService);
    private final transient ControlPanel controlPanel = new ControlPanel(securityService);
    private final transient SensorPanel sensorPanel = new SensorPanel(securityService);
    private final transient ImagePanel imagePanel = new ImagePanel(securityService);
    public CatpointGui() {
        super("Very Secure App");
        setLocation(100, 100);
        setSize(600, 850);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel mainPanel = new JPanel(new MigLayout("fillx, wrap 1"));
        mainPanel.add(displayPanel, "growx");
        mainPanel.add(imagePanel, "growx");
        mainPanel.add(controlPanel, "growx");
        mainPanel.add(sensorPanel, "growx");
        setContentPane(mainPanel);
        pack();
        setLocationRelativeTo(null);
    }
}