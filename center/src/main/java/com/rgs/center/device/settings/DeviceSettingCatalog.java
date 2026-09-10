package com.rgs.center.device.settings;

import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.SettingType;
import com.rgs.center.service.SettingsService;
import org.springframework.stereotype.Component;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.regex.Pattern;

/**
 * Allow-list and validation rules for settings editable from a browser.
 * Operational thresholds and scheduler intervals deliberately remain outside
 * this catalog until their runtime reconfiguration is designed separately.
 */
@Component
public class DeviceSettingCatalog {

    private static final Pattern COM_PORT =
            Pattern.compile("(?i)COM[1-9][0-9]{0,2}");

    private final Map<String, Definition> byId;
    private final Map<String, List<Definition>> byDevice;

    public DeviceSettingCatalog() {
        Map<String, Definition> definitions = new LinkedHashMap<>();

        add(definitions, DeviceCode.CAMERA, SettingsService.CAMERA_IP, SettingType.STRING);
        add(definitions, DeviceCode.LENOVO_PC, SettingsService.LENOVO_PC_IP, SettingType.STRING);

        add(definitions, DeviceCode.NICOLE, SettingsService.NICOLE_IP, SettingType.STRING);
        add(definitions, DeviceCode.NICOLE, SettingsService.NICOLE_PORT, SettingType.LONG);
        add(definitions, DeviceCode.NICOLE, SettingsService.NICOLE_CONNECT_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.NICOLE, SettingsService.NICOLE_READ_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.NICOLE, SettingsService.NICOLE_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG);

        add(definitions, DeviceCode.BOSON, SettingsService.BOSON_IP, SettingType.STRING);

        add(definitions, DeviceCode.ROBOTIC_MANIPULATOR, SettingsService.MANIPULATOR_HOST, SettingType.STRING);
        add(definitions, DeviceCode.ROBOTIC_MANIPULATOR, SettingsService.MANIPULATOR_COMMAND_PORT, SettingType.LONG);
        add(definitions, DeviceCode.ROBOTIC_MANIPULATOR, SettingsService.MANIPULATOR_FEEDBACK_PORT, SettingType.LONG);
        add(definitions, DeviceCode.ROBOTIC_MANIPULATOR, SettingsService.MANIPULATOR_CONNECT_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.ROBOTIC_MANIPULATOR, SettingsService.MANIPULATOR_READ_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.ROBOTIC_MANIPULATOR, SettingsService.MANIPULATOR_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG);

        add(definitions, DeviceCode.BARCODE_SCANNER, SettingsService.BARCODE_SCANNER_PORT, SettingType.STRING);
        add(definitions, DeviceCode.BARCODE_SCANNER, SettingsService.BARCODE_SCANNER_BAUD_RATE, SettingType.LONG);
        add(definitions, DeviceCode.BARCODE_SCANNER, SettingsService.BARCODE_SCANNER_READ_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.BARCODE_SCANNER, SettingsService.BARCODE_SCANNER_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG);

        add(definitions, DeviceCode.GDRM, SettingsService.GDRM_PORT, SettingType.STRING);
        add(definitions, DeviceCode.GDRM, SettingsService.GDRM_INITIAL_BAUD_RATE, SettingType.LONG);
        add(definitions, DeviceCode.GDRM, SettingsService.GDRM_READ_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.GDRM, SettingsService.GDRM_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG);

        add(definitions, DeviceCode.SCALES, SettingsService.SCALES_PORT, SettingType.STRING);
        add(definitions, DeviceCode.SCALES, SettingsService.SCALES_BAUD_RATE, SettingType.LONG);
        add(definitions, DeviceCode.SCALES, SettingsService.SCALES_READ_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.SCALES, SettingsService.SCALES_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG);

        add(definitions, DeviceCode.SHP, SettingsService.SHP_PORT, SettingType.STRING);
        add(definitions, DeviceCode.SHP, SettingsService.SHP_BAUD_RATE, SettingType.LONG);
        add(definitions, DeviceCode.SHP, SettingsService.SHP_READ_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.SHP, SettingsService.SHP_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG);

        add(definitions, DeviceCode.GAMMA_PRO, SettingsService.GAMMA_PRO_HOST, SettingType.STRING);
        add(definitions, DeviceCode.GAMMA_PRO, SettingsService.GAMMA_PRO_PORT, SettingType.LONG);
        add(definitions, DeviceCode.GAMMA_PRO, SettingsService.GAMMA_PRO_CONNECT_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.GAMMA_PRO, SettingsService.GAMMA_PRO_READ_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.GAMMA_PRO, SettingsService.GAMMA_PRO_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG);
        add(definitions, DeviceCode.GAMMA_PRO, SettingsService.GAMMA_PRO_CHARSET, SettingType.STRING);

        byId = Collections.unmodifiableMap(definitions);
        Map<String, List<Definition>> grouped = new LinkedHashMap<>();
        definitions.values().forEach(definition -> grouped
                .computeIfAbsent(definition.deviceCode(), ignored -> new ArrayList<>())
                .add(definition));
        grouped.replaceAll((ignored, values) -> List.copyOf(values));
        byDevice = Collections.unmodifiableMap(grouped);
    }

    public Definition require(String settingId) {
        Definition definition = byId.get(settingId);
        if (definition == null) {
            throw new NoSuchElementException("Unknown device setting: " + settingId);
        }
        return definition;
    }

    public List<Definition> requireDevice(String deviceCode) {
        List<Definition> definitions = byDevice.get(deviceCode);
        if (definitions == null) {
            throw new NoSuchElementException("Unknown device: " + deviceCode);
        }
        return definitions;
    }

    public List<Definition> definitions() {
        return List.copyOf(byId.values());
    }

    public Object convertAndValidate(Definition definition, Object rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException(definition.stringId() + " must not be null");
        }
        Object converted = switch (definition.type()) {
            case BOOLEAN -> requireBoolean(definition, rawValue);
            case LONG -> requireLong(definition, rawValue);
            case DOUBLE -> requireDouble(definition, rawValue);
            case STRING -> requireString(definition, rawValue);
        };
        validateRange(definition, converted);
        return converted;
    }

    public void validateStored(DeviceSetting setting) {
        Definition definition = require(setting.stringId());
        if (!definition.deviceCode().equals(setting.deviceCode())) {
            throw new IllegalStateException(
                    "Setting " + setting.stringId() + " belongs to "
                            + definition.deviceCode() + ", not " + setting.deviceCode()
            );
        }
        if (definition.type() != setting.type()) {
            throw new IllegalStateException("Invalid type for " + setting.stringId());
        }
        convertAndValidate(definition, setting.value());
    }

    private static void add(
            Map<String, Definition> target,
            DeviceCode device,
            String settingId,
            SettingType type
    ) {
        Definition definition = new Definition(device.getCode(), settingId, type);
        if (target.putIfAbsent(settingId, definition) != null) {
            throw new IllegalStateException("Duplicate setting definition: " + settingId);
        }
    }

    private static Boolean requireBoolean(Definition definition, Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        throw wrongType(definition);
    }

    private static Long requireLong(Definition definition, Object value) {
        if (!(value instanceof Number number)) {
            throw wrongType(definition);
        }
        double decimal = number.doubleValue();
        long integer = number.longValue();
        if (!Double.isFinite(decimal) || decimal != integer) {
            throw wrongType(definition);
        }
        return integer;
    }

    private static Double requireDouble(Definition definition, Object value) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) {
            return number.doubleValue();
        }
        throw wrongType(definition);
    }

    private static String requireString(Definition definition, Object value) {
        if (value instanceof String text && !text.isBlank()) {
            String normalized = text.trim();
            return definition.stringId().endsWith(".port")
                    ? normalized.toUpperCase(Locale.ROOT)
                    : normalized;
        }
        throw wrongType(definition);
    }

    private static void validateRange(Definition definition, Object value) {
        String id = definition.stringId();
        if (value instanceof String text) {
            if (text.length() > 255) {
                throw new IllegalArgumentException(id + " must not exceed 255 characters");
            }
            if (id.endsWith(".port") && !COM_PORT.matcher(text).matches()) {
                throw new IllegalArgumentException(id + " must be a COM port such as COM7");
            }
            if (id.endsWith(".charset") && !Charset.isSupported(text)) {
                throw new IllegalArgumentException(id + " contains an unsupported charset");
            }
            return;
        }
        if (value instanceof Long number) {
            if (id.endsWith("-port") || id.endsWith(".port")) {
                requireRange(id, number, 1, 65535);
            } else if (id.endsWith("baud-rate")) {
                requireRange(id, number, 300, 2_000_000);
            } else if (id.contains("timeout-millis")) {
                requireRange(id, number, 1, 300_000);
            }
        }
    }

    private static void requireRange(String id, long value, long minimum, long maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    id + " must be between " + minimum + " and " + maximum
            );
        }
    }

    private static IllegalArgumentException wrongType(Definition definition) {
        return new IllegalArgumentException(
                definition.stringId() + " must be " + definition.type()
        );
    }

    public record Definition(
            String deviceCode,
            String stringId,
            SettingType type
    ) {
    }
}
