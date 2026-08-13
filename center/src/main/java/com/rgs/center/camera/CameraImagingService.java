package com.rgs.center.camera;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/** Управляет текущим кадром, распознаванием и архивными JPEG-файлами. */
@Service
public class CameraImagingService implements CameraImagingOperations {

    private static final Logger log =
            LoggerFactory.getLogger(CameraImagingService.class);
    private static final DateTimeFormatter FILE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss_SSS");
    private static final String CONTENT_TYPE = "image/jpeg";

    private final CameraCaptureService captureService;
    private final BottleRecognitionEngine recognitionEngine;
    private final Path outputDirectory;
    private final Clock clock;

    private BufferedImage originalImage;
    private byte[] displayedImage;
    private long imageVersion;
    private boolean recognized;
    private String message = "Снимок ещё не сделан.";
    private String originalFile = "";
    private String detectedFile = "";
    private List<DetectedBottle> bottles = List.of();

    @Autowired
    public CameraImagingService(
            CameraCaptureService captureService,
            BottleRecognitionEngine recognitionEngine,
            @Value("${rgs.camera.output-directory:C:/camera}")
            String outputDirectory
    ) {
        this(
                captureService,
                recognitionEngine,
                Path.of(outputDirectory),
                Clock.systemDefaultZone()
        );
    }

    CameraImagingService(
            CameraCaptureService captureService,
            BottleRecognitionEngine recognitionEngine,
            Path outputDirectory,
            Clock clock
    ) {
        this.captureService = captureService;
        this.recognitionEngine = recognitionEngine;
        this.outputDirectory = outputDirectory.toAbsolutePath().normalize();
        this.clock = clock;
    }

    /** Получает новый оригинал и делает его текущим изображением. */
    @Override
    public synchronized CameraImagingSnapshot capture() {
        try {
            originalImage = copyRgb(captureService.capture());
            displayedImage = encodeJpeg(originalImage);
            advanceImageVersion();
            recognized = false;
            bottles = List.of();
            originalFile = "";
            detectedFile = "";
            message = captureService.isTestMode()
                    ? "Тестовый снимок загружен из " + captureService.captureFile()
                    : "Новый снимок получен с камеры";
            log.info("Camera frame captured: mode={}, size={}x{}",
                    captureService.isTestMode() ? "test" : "live",
                    originalImage.getWidth(), originalImage.getHeight());
            return snapshot();
        } catch (RuntimeException exception) {
            message = "Ошибка снимка: " + errorMessage(exception);
            log.warn("Camera capture failed: {}", errorMessage(exception));
            throw exception;
        }
    }

    /** Распознаёт текущий оригинал, сохраняет обе JPEG-копии и показывает разметку. */
    @Override
    public synchronized CameraImagingSnapshot recognize() {
        if (originalImage == null) {
            CameraImagingException exception = new CameraImagingException(
                    "Сначала сделайте снимок"
            );
            message = exception.getMessage();
            throw exception;
        }
        try {
            BottleRecognitionResult result = recognitionEngine.recognize(
                    copyRgb(originalImage)
            );
            String timestamp = LocalDateTime.now(clock).format(FILE_TIME);
            Files.createDirectories(outputDirectory);
            Path originalPath = outputDirectory.resolve(
                    "bottles_" + timestamp + ".jpeg"
            );
            Path detectedPath = outputDirectory.resolve(
                    "bottles_detected_" + timestamp + ".jpeg"
            );
            writeJpegAtomically(originalImage, originalPath);
            writeJpegAtomically(result.annotatedImage(), detectedPath);

            bottles = List.copyOf(result.bottles());
            displayedImage = encodeJpeg(result.annotatedImage());
            advanceImageVersion();
            recognized = true;
            originalFile = originalPath.toString();
            detectedFile = detectedPath.toString();
            message = bottles.isEmpty()
                    ? "Бутылки не обнаружены. Проверьте положение объектов и освещение."
                    : "Распознавание завершено. Найдено бутылок: " + bottles.size();
            log.info(
                    "Bottle recognition completed: bottles={}, original={}, detected={}",
                    bottles.size(), originalPath, detectedPath
            );
            return snapshot();
        } catch (IOException exception) {
            CameraImagingException wrapped = new CameraImagingException(
                    "Не удалось сохранить результат распознавания",
                    exception
            );
            message = "Ошибка распознавания: " + errorMessage(wrapped);
            throw wrapped;
        } catch (RuntimeException exception) {
            message = "Ошибка распознавания: " + errorMessage(exception);
            log.warn("Bottle recognition failed: {}", errorMessage(exception));
            throw exception;
        }
    }

    @Override
    public synchronized CameraImagingSnapshot snapshot() {
        return new CameraImagingSnapshot(
                displayedImage != null,
                imageVersion,
                recognized,
                message,
                originalFile,
                detectedFile,
                bottles
        );
    }

    @Override
    public synchronized Optional<CameraImagePayload> currentImage() {
        if (displayedImage == null) {
            return Optional.empty();
        }
        return Optional.of(new CameraImagePayload(
                displayedImage,
                CONTENT_TYPE,
                imageVersion
        ));
    }

    /** Очищает только состояние окна; архивные файлы не удаляются. */
    @Override
    public synchronized void reset() {
        originalImage = null;
        displayedImage = null;
        advanceImageVersion();
        recognized = false;
        message = "Снимок ещё не сделан.";
        originalFile = "";
        detectedFile = "";
        bottles = List.of();
    }

    /** Формирует уникальную версию URL, в том числе после перезапуска JAR. */
    private void advanceImageVersion() {
        imageVersion = Math.max(imageVersion + 1, clock.millis());
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(copyRgb(image), "jpeg", output)) {
                throw new CameraImagingException("JPEG encoder is unavailable");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new CameraImagingException(
                    "Не удалось подготовить JPEG для браузера",
                    exception
            );
        }
    }

    private static void writeJpegAtomically(
            BufferedImage image,
            Path destination
    ) throws IOException {
        Path temporary = Files.createTempFile(
                destination.getParent(),
                destination.getFileName().toString(),
                ".tmp"
        );
        boolean moved = false;
        try {
            try (var output = Files.newOutputStream(temporary)) {
                if (!ImageIO.write(copyRgb(image), "jpeg", output)) {
                    throw new IOException("JPEG encoder is unavailable");
                }
            }
            try {
                Files.move(
                        temporary,
                        destination,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static BufferedImage copyRgb(BufferedImage source) {
        BufferedImage copy = new BufferedImage(
                source.getWidth(),
                source.getHeight(),
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = copy.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return copy;
    }

    private static String errorMessage(Throwable exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
