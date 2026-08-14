package com.rgs.center.connection;

import com.rgs.center.objects.DeviceCode;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/** Reads per-device connection flags from the Spring environment. */
@Component
public class DeviceEnablementService {

    private final Environment environment;

    /** @param environment effective Spring configuration */
    public DeviceEnablementService(Environment environment) {
        this.environment = Objects.requireNonNull(environment);
    }

    /**
     * @param device device whose communication state is requested
     * @return {@code true} when the service may communicate with the device
     */
    public boolean isEnabled(DeviceCode device) {
        return environment.getProperty(
                device.getEnabledProperty(),
                Boolean.class,
                true
        );
    }
}
