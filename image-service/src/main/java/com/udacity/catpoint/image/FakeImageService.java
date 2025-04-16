package com.udacity.catpoint.image;
import java.awt.image.BufferedImage;
import java.util.Random;
public class FakeImageService implements ImageService{
    @Override
    public boolean imageContainsCat(BufferedImage image, float confidenceThreshold) {
        return false;
    }
}