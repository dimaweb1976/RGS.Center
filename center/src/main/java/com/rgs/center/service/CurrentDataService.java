package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

@Service
public class CurrentDataService {

    public static final String PINGED = "pinged";
    public static final String INTERACTION_LASTDATE_IN = "interaction_lastdate_in";
    public static final String INTERACTION_LASTDATE_OUT = "interaction_lastdate_out";
    public static final String DATA_AVAILABLE = "data_available";
    public static final String LAST_DATA = "last_data";
    public static final String LAST_ERROR = "last_error";

    private final Map<String, Map<String, Object>> currentData =
            new ConcurrentHashMap<>();

    @PostConstruct
    void initializeDevices() {
        for (DeviceCode device : DeviceCode.values()) {
            Map<String, Object> parameters = new ConcurrentHashMap<>();
            parameters.put(PINGED, false);
            parameters.put(INTERACTION_LASTDATE_IN, 0L);
            parameters.put(INTERACTION_LASTDATE_OUT, 0L);
            parameters.put(DATA_AVAILABLE, false);
            parameters.put(LAST_DATA, "");
            parameters.put(LAST_ERROR, "");
            currentData.put(device.getCode(), parameters);
        }
    }

    public void setValue(DeviceCode device, String parameter, Object value) {
        deviceData(device).put(parameter, value);
    }

    public Object getValue(DeviceCode device, String parameter) {
        Object value = deviceData(device).get(parameter);
        if (value == null) {
            throw new NoSuchElementException(
                    "Unknown parameter " + parameter + " for " + device.getCode()
            );
        }
        return value;
    }

    public <T> T getValue(DeviceCode device, String parameter, Class<T> valueClass) {
        return valueClass.cast(getValue(device, parameter));
    }

    public Map<String, Object> getDeviceData(DeviceCode device) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(deviceData(device))
        );
    }

    public Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        for (DeviceCode device : DeviceCode.values()) {
            snapshot.put(device.getCode(), getDeviceData(device));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    public void recordDataSuccess(DeviceCode device, Object value) {
        Map<String, Object> parameters = deviceData(device);
        parameters.put(LAST_DATA, value);
        parameters.put(DATA_AVAILABLE, true);
        parameters.put(LAST_ERROR, "");
        parameters.put(
                INTERACTION_LASTDATE_OUT,
                Instant.now().getEpochSecond()
        );
    }

    public void recordDataFailure(DeviceCode device, String error) {
        Map<String, Object> parameters = deviceData(device);
        parameters.put(DATA_AVAILABLE, false);
        parameters.put(LAST_ERROR, error == null ? "" : error);
    }

    private Map<String, Object> deviceData(DeviceCode device) {
        Map<String, Object> parameters = currentData.get(device.getCode());
        if (parameters == null) {
            throw new NoSuchElementException("Unknown device: " + device.getCode());
        }
        return parameters;
    }
}
