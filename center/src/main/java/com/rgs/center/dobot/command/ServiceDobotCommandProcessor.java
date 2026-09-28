package com.rgs.center.dobot.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ServiceDobotCommandProcessor {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public void processResponse(String command, String rawResponse) {
        if (rawResponse == null || rawResponse.isEmpty()) {
            logger.warn("Received empty response for command: {}", command);
            return;
        }

        String[] parts = rawResponse.split(",", 2);
        int errorId;
        try {
            errorId = Integer.parseInt(parts[0].trim());
        } catch (NumberFormatException e) {
            logger.error("Failed to parse errorID from response: '{}' for command: {}", rawResponse, command);
            return;
        }

        String value = parts.length > 1 ? parts[1].trim() : "";

        if (errorId == 0) {
            logger.info("Command '{}' executed successfully. Value: '{}'", command, value);
        } else {
            logger.error("Command '{}' failed with errorID: {}. Details: '{}'", command, errorId, value);
        }
    }
}