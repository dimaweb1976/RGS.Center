package com.rgs.center.ups;

import com.rgs.center.settings.RGSCenterSettings;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UpsIoLogger {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public synchronized void logSent(byte[] data) {
        logMessage("UPS> ", data);
    }

    public synchronized void logRespond(byte[] data) {
        logMessage("UPS< ", data);
    }

    private void logMessage(String prefix, byte[] data) {
        String logFilePath = settings.getSettingString("ups.io.logging.file");
        if (logFilePath == null || logFilePath.isEmpty()) {
            return;
        }

        String timestamp = LocalDateTime.now().format(FORMATTER);
        StringBuilder sb = new StringBuilder();
        if (data != null) {
            for (byte b : data) {
                sb.append(String.format("%02X ", b));
            }
        }
        String content = sb.toString().trim();
        String logLine = String.format("[%s] %s[%s]%n", timestamp, prefix, content);

        try (FileOutputStream fos = new FileOutputStream(new File(logFilePath), true)) {
            fos.write(logLine.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to write to UPS IO log file", e);
        }
    }
}