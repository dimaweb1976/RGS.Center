package com.rgs.center.camera;

import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

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

    @Test
    void numbersBottlesByRowsFromTopLeft() {
        List<DetectedBottle> detected = List.of(
                bottle(240, 68, 70),
                bottle(220, 16, 40),
                bottle(30, 62, 55),
                bottle(25, 25, 60),
                bottle(130, 20, 50)
        );

        List<DetectedBottle> numbered =
                BoofCvBottleRecognitionEngine.numberByRows(detected);

        assertEquals(
                List.of(25.0, 130.0, 220.0, 30.0, 240.0),
                numbered.stream().map(DetectedBottle::centerX).toList()
        );
        assertEquals(
                List.of(1, 2, 3, 4, 5),
                numbered.stream().map(DetectedBottle::number).toList()
        );
    }

    private static DetectedBottle bottle(
            double centerX,
            double centerY,
            int size
    ) {
        return new DetectedBottle(
                0,
                centerX,
                centerY,
                size,
                size,
                size,
                0.9
        );
    }

}
