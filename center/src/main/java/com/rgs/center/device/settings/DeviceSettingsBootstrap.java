package com.rgs.center.device.settings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

/** Loads database settings before the phase-0 device connection manager starts. */
@Component
public class DeviceSettingsBootstrap implements SmartLifecycle {

    private static final Logger log =
            LoggerFactory.getLogger(DeviceSettingsBootstrap.class);

    private final DeviceSettingsService settingsService;
    private final AtomicBoolean running = new AtomicBoolean();

    public DeviceSettingsBootstrap(DeviceSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            int loaded = settingsService.loadPersistedSettings();
            log.info("Loaded {} device connection settings from PostgreSQL", loaded);
        }
    }

    @Override
    public void stop() {
        running.set(false);
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public int getPhase() {
        return -100;
    }
}
