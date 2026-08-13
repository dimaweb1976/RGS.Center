package com.rgs.center.camera;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CameraImagingServiceTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void testModeLoadsBmpAndRecognitionSavesPairedJpegs() throws Exception {
        Path source = temporaryDirectory.resolve("bottles.bmp");
        BufferedImage input = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D sourceGraphics = input.createGraphics();
        sourceGraphics.setColor(Color.WHITE);
        sourceGraphics.fillRect(0, 0, input.getWidth(), input.getHeight());
        sourceGraphics.dispose();
        ImageIO.write(input, "bmp", source.toFile());

        CameraCaptureService capture = new CameraCaptureService(
                "test",
                "192.168.1.13",
                source.toString(),
                temporaryDirectory.resolve("SimpleGrab.exe").toString(),
                10_000
        );
        BottleRecognitionEngine detector = image -> {
            BufferedImage annotated = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB
            );
            Graphics2D graphics = annotated.createGraphics();
            graphics.drawImage(image, 0, 0, null);
            graphics.setColor(Color.GREEN);
            graphics.drawOval(70, 70, 60, 60);
            graphics.dispose();
            return new BottleRecognitionResult(
                    annotated,
                    List.of(new DetectedBottle(1, 100, 100, 60, 60, 60, 0.9))
            );
        };
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-13T11:35:27.184Z"),
                ZoneId.of("Europe/Riga")
        );
        CameraImagingService service = new CameraImagingService(
                capture, detector, temporaryDirectory, clock
        );

        CameraImagingSnapshot captured = service.capture();
        assertTrue(captured.imageAvailable());
        assertFalse(captured.recognized());

        CameraImagingSnapshot recognized = service.recognize();
        Path original = temporaryDirectory.resolve(
                "bottles_20260813_143527_184.jpeg"
        );
        Path detected = temporaryDirectory.resolve(
                "bottles_detected_20260813_143527_184.jpeg"
        );

        assertEquals(1, recognized.bottles().size());
        assertEquals(original.toString(), recognized.originalFile());
        assertEquals(detected.toString(), recognized.detectedFile());
        assertTrue(Files.size(original) > 0);
        assertTrue(Files.size(detected) > 0);
        assertTrue(service.currentImage().isPresent());
    }

    @Test
    void imagePayloadProtectsInternalByteArray() {
        byte[] source = {1, 2, 3};
        CameraImagePayload payload = new CameraImagePayload(source, "image/jpeg", 1);
        source[0] = 9;
        byte[] returned = payload.content();
        returned[1] = 8;

        assertArrayEquals(new byte[]{1, 2, 3}, payload.content());
    }
}
