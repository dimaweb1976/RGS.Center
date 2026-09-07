package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;

/**
 * Потокобезопасная витрина последних известных состояний устройств.
 *
 * <p>Витрина хранит только актуальные оперативные значения и не заменяет
 * историческую БД. Вложенные карты разделены по постоянному {@link DeviceCode}.</p>
 */
@Service
public class CurrentDataService {

    private static final Logger log =
            LoggerFactory.getLogger(CurrentDataService.class);

    public static final String PINGED = "pinged";
    public static final String INTERACTION_LASTDATE_IN = "interaction_lastdate_in";
    public static final String INTERACTION_LASTDATE_OUT = "interaction_lastdate_out";
    public static final String DATA_AVAILABLE = "data_available";
    public static final String LAST_DATA = "last_data";
    public static final String LAST_ERROR = "last_error";

    private final Map<String, Map<String, Object>> currentData =
            new ConcurrentHashMap<>();

    /** Создаёт начальный набор параметров для каждого зарегистрированного устройства. */
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
        log.info(
                "Current data view initialized for {} devices",
                currentData.size()
        );
    }

    /**
     * Записывает произвольный параметр устройства.
     *
     * @param device код устройства
     * @param parameter имя параметра
     * @param value новое значение
     */
    public void setValue(DeviceCode device, String parameter, Object value) {
        deviceData(device).put(parameter, value);
    }

    /**
     * Атомарно публикует согласованный набор параметров одного устройства.
     * Читатель увидит либо предыдущую, либо целиком новую версию карты.
     *
     * @param device код устройства
     * @param values параметры одного согласованного снимка
     */
    public void setValues(DeviceCode device, Map<String, ?> values) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(values, "values");
        currentData.compute(device.getCode(), (code, existing) -> {
            if (existing == null) {
                throw new NoSuchElementException("Unknown device: " + code);
            }
            Map<String, Object> replacement =
                    new ConcurrentHashMap<>(existing);
            values.forEach((parameter, value) -> replacement.put(
                    Objects.requireNonNull(parameter, "parameter"),
                    Objects.requireNonNull(value, "value")
            ));
            return replacement;
        });
    }

    /**
     * @param device код устройства
     * @param parameter имя параметра
     * @return текущее значение
     */
    public Object getValue(DeviceCode device, String parameter) {
        Object value = deviceData(device).get(parameter);
        if (value == null) {
            throw new NoSuchElementException(
                    "Unknown parameter " + parameter + " for " + device.getCode()
            );
        }
        return value;
    }

    /**
     * Возвращает значение с проверкой Java-типа.
     *
     * @param device код устройства
     * @param parameter имя параметра
     * @param valueClass ожидаемый класс
     * @param <T> тип результата
     * @return текущее значение
     */
    public <T> T getValue(DeviceCode device, String parameter, Class<T> valueClass) {
        return valueClass.cast(getValue(device, parameter));
    }

    /**
     * @param device код устройства
     * @return неизменяемая копия параметров одного устройства
     */
    public Map<String, Object> getDeviceData(DeviceCode device) {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(deviceData(device))
        );
    }

    /**
     * @return неизменяемый снимок всей витрины
     */
    public Map<String, Map<String, Object>> snapshot() {
        Map<String, Map<String, Object>> snapshot = new LinkedHashMap<>();
        for (DeviceCode device : DeviceCode.values()) {
            snapshot.put(device.getCode(), getDeviceData(device));
        }
        return Collections.unmodifiableMap(snapshot);
    }

    /**
     * Фиксирует успешное получение прикладных данных.
     *
     * @param device код устройства
     * @param value полученное значение
     */
    public void recordDataSuccess(DeviceCode device, Object value) {
        Map<String, Object> parameters = deviceData(device);
        parameters.put(LAST_DATA, value);
        parameters.put(DATA_AVAILABLE, true);
        parameters.put(LAST_ERROR, "");
        parameters.put(
                INTERACTION_LASTDATE_OUT,
                Instant.now().getEpochSecond()
        );
        log.debug("Current data updated successfully for device {}", device);
    }

    /**
     * Фиксирует ошибку getData, сохраняя последнее успешное значение отдельно.
     *
     * @param device код устройства
     * @param error диагностическое сообщение
     */
    public void recordDataFailure(DeviceCode device, String error) {
        Map<String, Object> parameters = deviceData(device);
        parameters.put(DATA_AVAILABLE, false);
        parameters.put(LAST_ERROR, error == null ? "" : error);
        // Device services own transition-based WARN/ERROR logging. This
        // low-level update may be called on every scheduled retry.
        log.debug(
                "Current data update failed for device {}: {}",
                device,
                error == null || error.isBlank() ? "<no details>" : error
        );
    }

    /**
     * Возвращает внутреннюю карту зарегистрированного устройства.
     *
     * @param device код устройства
     * @return изменяемая потокобезопасная карта
     */
    private Map<String, Object> deviceData(DeviceCode device) {
        Map<String, Object> parameters = currentData.get(device.getCode());
        if (parameters == null) {
            throw new NoSuchElementException("Unknown device: " + device.getCode());
        }
        return parameters;
    }
}
