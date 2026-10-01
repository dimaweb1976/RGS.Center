package com.rgs.center.settings;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RGSCenterSettings {
    final ConcurrentHashMap< String, Object> settings;
    
    private final JdbcTemplate jdbcTemplate;
    
    private static final Logger log = LoggerFactory.getLogger(RGSCenterSettings.class);
    
    public RGSCenterSettings(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        settings = new ConcurrentHashMap<>();        
    }
    
    @PostConstruct
    public void init() {
        settings.put( "shp.queue_service.period_ms", 200L);
        settings.put( "shp.poller_service.period_ms", 1000L);
        
        // Проверяем переменную окружения для логирования обмена с GammaPro
        String envLogFile = System.getenv("RGSCENTER_GAMMAPRO_IO_LOGGING_FILE");
        if (envLogFile != null && !envLogFile.trim().isEmpty()) {
            java.io.File logFile = new java.io.File(envLogFile.trim());
            try {
                // Пытаемся создать или очистить файл для наполнения
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, false)) {
                    // Файл успешно открыт и очищен
                }
                settings.put("gammapro.io.logging.file", logFile.getAbsolutePath());
                log.info("GammaPro IO logging enabled to file: {}", logFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to initialize GammaPro IO logging file at: " + envLogFile, e);
            }
        }
        
        // Проверяем переменную окружения для логирования обмена с Nicole
        String envNicoleLogFile = System.getenv("RGSCENTER_NICOLE_IO_LOGGING_FILE");
        if (envNicoleLogFile != null && !envNicoleLogFile.trim().isEmpty()) {
            java.io.File logFile = new java.io.File(envNicoleLogFile.trim());
            try {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, false)) {
                    // Файл успешно открыт и очищен
                }
                settings.put("nicole.io.logging.file", logFile.getAbsolutePath());
                log.info("Nicole IO logging enabled to file: {}", logFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to initialize Nicole IO logging file at: " + envNicoleLogFile, e);
            }
        }
        
        // Проверяем переменную окружения для логирования обмена со Scales
        String envScalesLogFile = System.getenv("RGSCENTER_SCALES_IO_LOGGING_FILE");
        if (envScalesLogFile != null && !envScalesLogFile.trim().isEmpty()) {
            java.io.File logFile = new java.io.File(envScalesLogFile.trim());
            try {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, false)) {
                    // Файл успешно открыт и очищен
                }
                settings.put("scales.io.logging.file", logFile.getAbsolutePath());
                log.info("Scales IO logging enabled to file: {}", logFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to initialize Scales IO logging file at: " + envScalesLogFile, e);
            }
        }
        
        
        // Проверяем переменную окружения для логирования обмена с SHP
        String envShpLogFile = System.getenv("RGSCENTER_SHP_IO_LOGGING_FILE");
        if (envShpLogFile != null && !envShpLogFile.trim().isEmpty()) {
            java.io.File logFile = new java.io.File(envShpLogFile.trim());
            try {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, false)) {
                    // Файл успешно открыт и очищен
                }
                settings.put("shp.io.logging.file", logFile.getAbsolutePath());
                log.info("SHP IO logging enabled to file: {}", logFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to initialize SHP IO logging file at: " + envShpLogFile, e);
            }
        }
        
        // Проверяем переменную окружения для логирования обмена с Dobot (команды)
        String envDobotLogFile = System.getenv("RGSCENTER_DOBOT_COMMAND_IO_LOGGING_FILE");
        if (envDobotLogFile != null && !envDobotLogFile.trim().isEmpty()) {
            java.io.File logFile = new java.io.File(envDobotLogFile.trim());
            try {
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(logFile, false)) {
                    // Файл успешно открыт и очищен
                }
                settings.put("dobot.command.io.logging.file", logFile.getAbsolutePath());
                log.info("Dobot Command IO logging enabled to file: {}", logFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("Failed to initialize Dobot Command IO logging file at: " + envDobotLogFile, e);
            }
        }
        
        
        String sql = "SELECT string_id, type, long_value, string_value FROM public.device_settings";
        
        try {
            jdbcTemplate.query(sql, (rs, rowNum) -> {
                String key = rs.getString("string_id");
                int type = rs.getInt("type");
                
                if (type == 2) {
                    long longVal = rs.getLong("long_value");
                    if (!rs.wasNull()) {
                        settings.put(key, longVal);
                    }
                } else if (type == 4) {
                    String strVal = rs.getString("string_value");
                    if (strVal != null) {
                        settings.put(key, strVal);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.error("Failed to load settings from database", e);
        }
    }
    
    public Object getSetting( String key) {
        return settings.get( key);
    }
    
    public Long getSettingLong(String key) {
        Object val = settings.get(key);
        return (val instanceof Long) ? (Long) val : null;
    }

    public String getSettingString(String key) {
        Object val = settings.get(key);
        return (val instanceof String) ? (String) val : null;
    }
    
    // Update setting value in memory and database
    public void updateSetting(String key, Object value) {
        if (key == null || value == null) {
            return;
        }

        String sql;
        int updatedRows = 0;

        if (value instanceof Long) {
            sql = "UPDATE public.device_settings SET long_value = ? WHERE string_id = ? AND type = 2";
            updatedRows = jdbcTemplate.update(sql, (Long) value, key);
        } else if (value instanceof String) {
            sql = "UPDATE public.device_settings SET string_value = ? WHERE string_id = ? AND type = 4";
            updatedRows = jdbcTemplate.update(sql, (String) value, key);
        }

        // If database update was successful, update the in-memory map
        if (updatedRows > 0) {
            settings.put(key, value);
            log.info("Setting '{}' updated successfully to {}", key, value);
        } else {
            log.warn("Failed to update setting '{}' in database or setting not found", key);
        }
    }
}
