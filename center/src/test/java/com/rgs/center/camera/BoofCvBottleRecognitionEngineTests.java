package com.rgs.center.camera;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class BoofCvBottleRecognitionEngineTests {

    @Test
    void processesBlankFrameWithoutFailure() {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.dispose();

        BoofCvBottleRecognitionEngine engine = new BoofCvBottleRecognitionEngine(
                40, 5, 20, 0.005, 2, 40,
                20, 180, 0.78, 1.22, 0.55,
                0.18, 0.30, 0.90, 0.35
        );

        BottleRecognitionResult result = engine.recognize(image);
        assertNotNull(result.annotatedImage());
        assertNotNull(result.bottles());
    }

}
