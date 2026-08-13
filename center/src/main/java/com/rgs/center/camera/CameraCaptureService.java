package com.rgs.center.camera;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Получает одиночный кадр камеры в тестовом или рабочем режиме.
 * В тестовом режиме внешний процесс не запускается.
 */
@Service
public class CameraCaptureService {

    private static final String TEST_MODE = "test";
    private static final String LIVE_MODE = "live";

    private final String mode;
    private final String cameraIp;
    private final Path captureFile;
    private final Path executable;
    private final Duration timeout;

    public CameraCaptureService(
            @Value("${rgs.camera.capture-mode:test}") String mode,
            @Value("${rgs.device.camera.ip:192.168.1.13}") String cameraIp,
            @Value("${rgs.camera.capture-file:C:/camera/bottles.bmp}")
            String captureFile,
            @Value("${rgs.camera.capture-executable:C:/SimpleGrab/SimpleGrab.exe}")
            String executable,
            @Value("${rgs.camera.capture-timeout-millis:10000}") long timeoutMillis
    ) {
        this.mode = mode.trim().toLowerCase(Locale.ROOT);
        this.cameraIp = cameraIp.trim();
        this.captureFile = Path.of(captureFile).toAbsolutePath().normalize();
        this.executable = Path.of(executable).toAbsolutePath().normalize();
        if (timeoutMillis <= 0) {
            throw new IllegalArgumentException("Camera capture timeout must be positive");
        }
        timeout = Duration.ofMillis(timeoutMillis);
        if (!TEST_MODE.equals(this.mode) && !LIVE_MODE.equals(this.mode)) {
            throw new IllegalArgumentException(
                    "Camera capture mode must be test or live"
            );
        }
    }

    /** Возвращает новый кадр; в test-режиме читает только готовый BMP. */
    public BufferedImage capture() {
        if (LIVE_MODE.equals(mode)) {
            runSimpleGrab();
        }
        if (!Files.isRegularFile(captureFile)) {
            throw new CameraImagingException(
                    "Файл снимка не найден: " + captureFile
            );
        }
        try {
            BufferedImage image = ImageIO.read(captureFile.toFile());
            if (image == null) {
                throw new CameraImagingException(
                        "Формат снимка не поддерживается: " + captureFile
                );
            }
            return image;
        } catch (IOException exception) {
            throw new CameraImagingException(
                    "Не удалось прочитать снимок: " + captureFile,
                    exception
            );
        }
    }

    public boolean isTestMode() {
        return TEST_MODE.equals(mode);
    }

    public Path captureFile() {
        return captureFile;
    }

    /** Запускает штатную утилиту Basler и контролирует её тайм-аут. */
    private void runSimpleGrab() {
        if (!Files.isRegularFile(executable)) {
            throw new CameraImagingException(
                    "Утилита захвата не найдена: " + executable
            );
        }
        try {
            Path parent = captureFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Process process = new ProcessBuilder(
                    executable.toString(),
                    cameraIp,
                    captureFile.toString(),
                    Long.toString(timeout.toMillis())
            ).redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            boolean finished = process.waitFor(
                    timeout.plusSeconds(2).toMillis(),
                    TimeUnit.MILLISECONDS
            );
            if (!finished) {
                process.destroyForcibly();
                throw new CameraImagingException(
                        "Превышено время ожидания снимка камеры"
                );
            }
            if (process.exitValue() != 0) {
                throw new CameraImagingException(
                        "SimpleGrab завершился с кодом " + process.exitValue()
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CameraImagingException(
                    "Получение снимка было прервано",
                    exception
            );
        } catch (IOException exception) {
            throw new CameraImagingException(
                    "Не удалось запустить SimpleGrab",
                    exception
            );
        }
    }
}
