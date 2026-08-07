package com.pixel.intelligentsearch;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ImageProcessor {
    public static void main(String[] args) throws Exception {
        File dir = new File("src/main/res/drawable");
        if (!dir.exists()) {
            dir = new File("app/src/main/res/drawable");
        }
        File[] files = dir.listFiles((d, name) -> name.startsWith("doodle_") && name.endsWith(".png"));
        int count = 0;
        if (files != null) {
            for (File file : files) {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    BufferedImage newImg = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < img.getHeight(); y++) {
                        for (int x = 0; x < img.getWidth(); x++) {
                            int rgb = img.getRGB(x, y);
                            Color c = new Color(rgb, true);
                            if (c.getRed() > 240 && c.getGreen() > 240 && c.getBlue() > 240) {
                                newImg.setRGB(x, y, 0x00FFFFFF);
                            } else {
                                newImg.setRGB(x, y, rgb);
                            }
                        }
                    }
                    ImageIO.write(newImg, "png", file);
                    count++;
                }
            }
        }
        System.out.println("Processed " + count + " images.");
    }
}
