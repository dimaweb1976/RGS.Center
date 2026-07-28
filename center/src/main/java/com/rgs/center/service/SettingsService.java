package com.rgs.center.service;

import com.rgs.center.objects.SettingRecord;
import com.rgs.center.objects.SettingType;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SettingsService {

    public static final String CAMERA_IP = "device.camera.ip";
    public static final String ROBOPLC_IP = "device.roboplc.ip";
    public static final String NICOLE_IP = "device.nicole.ip";
    public static final String BOSON_IP = "device.boson.ip";
    public static final String BARCODE_SCANNER_PORT = "device.barcode-scanner.port";
    public static final String BARCODE_SCANNER_BAUD_RATE =
            "device.barcode-scanner.baud-rate";
    public static final String BARCODE_SCANNER_READ_TIMEOUT_MILLIS =
            "device.barcode-scanner.read-timeout-millis";
    public static final String BARCODE_SCANNER_MAX_CODE_LENGTH =
            "device.barcode-scanner.max-code-length";
    public static final String PING_TIMEOUT_MILLIS = "ping.timeout.millis";

    private final Map<String, Integer> settingTypes = new ConcurrentHashMap<>();
    private final Map<String, Object> settingValues = new ConcurrentHashMap<>();

    @Value("${rgs.device.camera.ip:192.168.1.13}")
    private String cameraIp = "192.168.1.13";

    @Value("${rgs.device.roboplc.ip:}")
    private String roboplcIp = "";

    @Value("${rgs.device.nicole.ip:}")
    private String nicoleIp = "";

    @Value("${rgs.device.boson.ip:192.168.1.16}")
    private String bosonIp = "192.168.1.16";

    @Value("${rgs.device.barcode-scanner.port:}")
    private String barcodeScannerPort = "";

    @Value("${rgs.device.barcode-scanner.baud-rate:9600}")
    private long barcodeScannerBaudRate = 9600L;

    @Value("${rgs.device.barcode-scanner.read-timeout-millis:5000}")
    private long barcodeScannerReadTimeoutMillis = 5000L;

    @Value("${rgs.device.barcode-scanner.max-code-length:4096}")
    private long barcodeScannerMaxCodeLength = 4096L;

    @PostConstruct
    void initializeDefaults() {
        registerDefault(CAMERA_IP, SettingType.STRING, cameraIp);
        registerDefault(ROBOPLC_IP, SettingType.STRING, roboplcIp);
        registerDefault(NICOLE_IP, SettingType.STRING, nicoleIp);
        registerDefault(BOSON_IP, SettingType.STRING, bosonIp);
        registerDefault(
                BARCODE_SCANNER_PORT,
                SettingType.STRING,
                barcodeScannerPort
        );
        registerDefault(
                BARCODE_SCANNER_BAUD_RATE,
                SettingType.LONG,
                barcodeScannerBaudRate
        );
        registerDefault(
                BARCODE_SCANNER_READ_TIMEOUT_MILLIS,
                SettingType.LONG,
                barcodeScannerReadTimeoutMillis
        );
        registerDefault(
                BARCODE_SCANNER_MAX_CODE_LENGTH,
                SettingType.LONG,
                barcodeScannerMaxCodeLength
        );
        registerDefault(PING_TIMEOUT_MILLIS, SettingType.LONG, 3000L);
    }

    public void registerDefault(String code, SettingType type, Object value) {
        validate(code, type, value);
        settingTypes.putIfAbsent(code, type.getCode());
        settingValues.putIfAbsent(code, value);
    }

    public int getType(String code) {
        Integer type = settingTypes.get(code);
        if (type == null) {
            throw new NoSuchElementException("Unknown setting: " + code);
        }
        return type;
    }

    public Object getValue(String code) {
        Object value = settingValues.get(code);
        if (value == null) {
            throw new NoSuchElementException("Unknown setting: " + code);
        }
        return value;
    }

    public <T> T getValue(String code, Class<T> valueClass) {
        return valueClass.cast(getValue(code));
    }

    public void setValue(String code, Object value) {
        SettingType type = SettingType.fromCode(getType(code));
        validate(code, type, value);
        settingValues.put(code, value);
    }

    public void load(Collection<SettingRecord> records) {
        for (SettingRecord record : records) {
            SettingType type = SettingType.fromCode(record.type());
            Object value = record.value();
            validate(record.stringId(), type, value);
            settingTypes.put(record.stringId(), type.getCode());
            settingValues.put(record.stringId(), value);
        }
    }

    public List<SettingRecord> dump() {
        return settingTypes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    SettingType type = SettingType.fromCode(entry.getValue());
                    return SettingRecord.from(
                            entry.getKey(),
                            type,
                            settingValues.get(entry.getKey())
                    );
                })
                .toList();
    }

    private void validate(String code, SettingType type, Object value) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Setting code must not be blank");
        }
        if (!type.accepts(value)) {
            throw new IllegalArgumentException(
                    "Value for setting " + code + " must be " + type
            );
        }
    }
}
