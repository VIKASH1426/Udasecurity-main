package com.udacity.catpoint.application;
import com.udacity.catpoint.data.AlarmStatus;
import com.udacity.catpoint.service.SecurityService;
import com.udacity.catpoint.service.StyleService;
import net.miginfocom.swing.MigLayout;
import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.Serial;
public class ImagePanel extends JPanel implements StatusListener, java.io.Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private final SecurityService securityService;
    private transient JLabel cameraHeader;
    private transient JLabel cameraLabel;
    private transient BufferedImage currentCameraImage;
    private final int IMAGE_WIDTH = 300;
    private final int IMAGE_HEIGHT = 225;
    public ImagePanel(SecurityService securityService){
        super();
        setLayout(new MigLayout());
        if (securityService == null) {
            throw new IllegalArgumentException("SecurityService cannot be null");
        }
        this.securityService = securityService;
        this.securityService.addStatusListener(this);

        cameraHeader = new JLabel("Camera Feed");
        cameraHeader.setFont(StyleService.HEADING_FONT);

        cameraLabel = new JLabel();
        cameraLabel.setBackground(Color.WHITE);
        cameraLabel.setPreferredSize(new Dimension(IMAGE_WIDTH, IMAGE_HEIGHT));
        cameraLabel.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
        JButton addPictureButton=new JButton("Refresh Camera");
        addPictureButton.addActionListener(e -> {
            JFileChooser chooser =new JFileChooser();
            chooser.setCurrentDirectory(new File("."));
            chooser.setDialogTitle("Select Picture");
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            if(chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            try{
                File selectedFile= chooser.getSelectedFile();
                if (selectedFile != null){
                    currentCameraImage = ImageIO.read(selectedFile);
                    if (currentCameraImage != null){
                        Image tmp = new ImageIcon(currentCameraImage).getImage();
                        cameraLabel.setIcon(new ImageIcon(tmp.getScaledInstance(IMAGE_WIDTH, IMAGE_HEIGHT, Image.SCALE_SMOOTH)));
                    } else{
                        JOptionPane.showMessageDialog(this, "Could not read image file.", "Image Error", JOptionPane.ERROR_MESSAGE);
                        currentCameraImage = null;
                    }
                }
            } catch(IOException ioe){
                JOptionPane.showMessageDialog(this, "Invalid image selected or IO error: " + ioe.getMessage(), "Image Error", JOptionPane.ERROR_MESSAGE);
                currentCameraImage = null;
            }
            repaint();
        });
        JButton scanPictureButton=new JButton("Scan Picture");
        scanPictureButton.addActionListener(e -> {
            this.securityService.processImage(currentCameraImage);
        });
        add(cameraHeader, "span 3, wrap");
        add(cameraLabel, "span 3, wrap");
        add(addPictureButton);
        add(scanPictureButton);
    }
    @Override
    public void notify(AlarmStatus status){}
    @Override
    public void catDetected(boolean catDetected){
        SwingUtilities.invokeLater(() -> {
            if(catDetected) {
                cameraHeader.setText("DANGER - CAT DETECTED");
            } else {
                cameraHeader.setText("Camera Feed - No Cats Detected");
            }
        });
    }
    @Override
    public void sensorStatusChanged() {}
}