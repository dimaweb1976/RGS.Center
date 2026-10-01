package com.rgs.center.dobot.command;

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
public class DobotCommandIoLogger {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterSettings settings;
    
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public synchronized void logSent(String command) {
        logMessage("RGS> ", command);
    }

    public synchronized void logRespond(String response) {
        logMessage("RGS< ", response);
    }

    private void logMessage(String prefix, String content) {
        String logFilePath = settings != null ? settings.getSettingString("dobot.command.io.logging.file") : null;
        if (logFilePath == null || logFilePath.isEmpty()) {
            return;
        }

        String timestamp = LocalDateTime.now().format(FORMATTER);
        String cleanContent = content != null ? content.trim() : "";
        String logLine = String.format("[%s] %s[%s]%n", timestamp, prefix, cleanContent);

        try (FileOutputStream fos = new FileOutputStream(new File(logFilePath), true)) {
            fos.write(logLine.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            logger.error("Failed to write to Dobot command IO log file", e);
        }
    }
}