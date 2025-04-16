package com.udacity.catpoint.image; // Updated package

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider; // Example provider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.rekognition.RekognitionClient;
import software.amazon.awssdk.services.rekognition.model.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.stream.Collectors;
public class AwsImageService implements ImageService {
    private final Logger log = LoggerFactory.getLogger(AwsImageService.class);
    private RekognitionClient rekognitionClient;
    public AwsImageService(){
        try{
            AwsCredentialsProvider credentialsProvider = DefaultCredentialsProvider.create();
            String awsRegion = System.getenv("AWS_REGION");
            if (awsRegion == null) {
                awsRegion = "us-east-1";
                log.warn("AWS Region not specified via AWS_REGION env var, defaulting to {}", awsRegion);
            }

            this.rekognitionClient = RekognitionClient.builder()
                    .credentialsProvider(credentialsProvider)
                    .region(Region.of(awsRegion))
                    .build();
            log.info("AWS Rekognition client initialized successfully for region: {}", awsRegion);
        } catch (Exception e) {
            log.error("Failed to initialize AWS Rekognition client: {}. Ensure credentials and region are configured correctly.", e.getMessage(), e);
            this.rekognitionClient = null;
        }
    }
    @Override
    public boolean imageContainsCat(BufferedImage image, float confidenceThreshold) {
        if (rekognitionClient == null) {
            log.error("AWS Rekognition client not initialized. Cannot process image.");
            return false;
        }
        if (image == null) {
            log.warn("Input image is null, cannot detect cats.");
            return false;
        }
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", os);
            SdkBytes imageBytes = SdkBytes.fromByteArray(os.toByteArray());
            Image awsImage = Image.builder().bytes(imageBytes).build();

            DetectLabelsRequest detectLabelsRequest = DetectLabelsRequest.builder()
                    .image(awsImage)
                    .maxLabels(20)
                    .minConfidence(confidenceThreshold)
                    .build();
            DetectLabelsResponse response = rekognitionClient.detectLabels(detectLabelsRequest);
            logLabels(response);
            return response.labels().stream()
                    .anyMatch(label -> "cat".equalsIgnoreCase(label.name()));

        } catch (IOException e) {
            log.error("Error converting BufferedImage to byte array", e);
            return false;
        } catch (RekognitionException e) {
            log.error("AWS Rekognition API error: {}", e.getMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("An unexpected error occurred during image processing", e);
            return false;
        }
    }
    private void logLabels(DetectLabelsResponse response) {
        if (response != null && response.hasLabels()) {
            String detected = response.labels().stream()
                    .map(label -> String.format("%s(%.1f%%)", label.name(), label.confidence()))
                    .collect(Collectors.joining(", "));
            log.info("AWS Rekognition detected labels: [{}]", detected);
        } else {
            log.info("AWS Rekognition detected no labels matching criteria.");
        }
    }
}