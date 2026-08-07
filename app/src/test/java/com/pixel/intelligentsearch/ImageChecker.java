package com.pixel.intelligentsearch;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ImageChecker {
    public static void main(String[] args) throws Exception {
        File file = new File("app/src/main/res/drawable/doodle_christmas_0.png");
        BufferedImage img = ImageIO.read(file);
        int transparentCount = 0;
        int opaqueCount = 0;
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int rgb = img.getRGB(x, y);
                Color c = new Color(rgb, true);
                if (c.getAlpha() < 255) {
                    transparentCount++;
                } else {
                    opaqueCount++;
                }
            }
        }
        System.out.println("Transparent pixels: " + transparentCount);
        System.out.println("Opaque pixels: " + opaqueCount);
    }
}
