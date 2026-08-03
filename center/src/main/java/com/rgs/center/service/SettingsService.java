package com.rgs.center.service;

import com.rgs.center.objects.SettingRecord;
import com.rgs.center.objects.SettingType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Потокобезопасное хранилище типов и текущих значений настроек.
 *
 * <p>Начальные значения загружаются из application.properties. Методы
 * {@link #load(Collection)} и {@link #dump()} являются границей для будущего
 * постоянного хранилища.</p>
 */
@Service
public class SettingsService {

    private static final Logger log =
            LoggerFactory.getLogger(SettingsService.class);

    public static final String CAMERA_IP = "device.camera.ip";
    public static final String ROBOPLC_IP = "device.roboplc.ip";
    public static final String NICOLE_IP = "device.nicole.ip";
    public static final String BOSON_IP = "device.boson.ip";
    public static final String MANIPULATOR_HOST = "device.manipulator.host";
    public static final String MANIPULATOR_COMMAND_PORT = "device.manipulator.command-port";
    public static final String MANIPULATOR_FEEDBACK_PORT = "device.manipulator.feedback-port";
    public static final String MANIPULATOR_CONNECT_TIMEOUT_MILLIS = "device.manipulator.connect-timeout-millis";
    public static final String MANIPULATOR_READ_TIMEOUT_MILLIS = "device.manipulator.read-timeout-millis";
    public static final String MANIPULATOR_RESPONSE_TIMEOUT_MILLIS = "device.manipulator.response-timeout-millis";
    public static final String MANIPULATOR_TELEMETRY_STALE_MILLIS = "device.manipulator.telemetry-stale-millis";
    public static final String MANIPULATOR_HATCH_CLOSED_DI_BIT = "device.manipulator.hatch-closed-di-bit";
    public static final String MANIPULATOR_HATCH_OPEN_DI_BIT = "device.manipulator.hatch-open-di-bit";
    public static final String BARCODE_SCANNER_PORT = "device.barcode-scanner.port";
    public static final String BARCODE_SCANNER_BAUD_RATE =
            "device.barcode-scanner.baud-rate";
    public static final String BARCODE_SCANNER_READ_TIMEOUT_MILLIS =
            "device.barcode-scanner.read-timeout-millis";
    public static final String BARCODE_SCANNER_MAX_CODE_LENGTH =
            "device.barcode-scanner.max-code-length";
    public static final String GDRM_PORT = "device.gdrm.port";
    public static final String GDRM_INITIAL_BAUD_RATE =
            "device.gdrm.initial-baud-rate";
    public static final String GDRM_READ_TIMEOUT_MILLIS =
            "device.gdrm.read-timeout-millis";
    public static final String GDRM_RESPONSE_TIMEOUT_MILLIS =
            "device.gdrm.response-timeout-millis";
    public static final String SCALES_PORT = "device.scales.port";
    public static final String SHP_PORT = "device.shp.port";
    public static final String SHP_BAUD_RATE = "device.shp.baud-rate";
    public static final String SHP_READ_TIMEOUT_MILLIS =
            "device.shp.read-timeout-millis";
    public static final String SHP_RESPONSE_TIMEOUT_MILLIS =
            "device.shp.response-timeout-millis";
    public static final String GAMMA_PRO_HOST = "device.gamma-pro.host";
    public static final String GAMMA_PRO_PORT = "device.gamma-pro.port";
    public static final String GAMMA_PRO_CONNECT_TIMEOUT_MILLIS =
            "device.gamma-pro.connect-timeout-millis";
    public static final String GAMMA_PRO_READ_TIMEOUT_MILLIS =
            "device.gamma-pro.read-timeout-millis";
    public static final String GAMMA_PRO_RESPONSE_TIMEOUT_MILLIS =
            "device.gamma-pro.response-timeout-millis";
    public static final String GAMMA_PRO_CHARSET =
            "device.gamma-pro.charset";
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

    @Value("${rgs.device.manipulator.host:192.168.5.1}")
    private String manipulatorHost = "192.168.5.1";

    @Value("${rgs.device.manipulator.command-port:29999}")
    private long manipulatorCommandPort = 29999L;

    @Value("${rgs.device.manipulator.feedback-port:30005}")
    private long manipulatorFeedbackPort = 30005L;

    @Value("${rgs.device.manipulator.connect-timeout-millis:2000}")
    private long manipulatorConnectTimeoutMillis = 2000L;

    @Value("${rgs.device.manipulator.read-timeout-millis:250}")
    private long manipulatorReadTimeoutMillis = 250L;

    @Value("${rgs.device.manipulator.response-timeout-millis:3000}")
    private long manipulatorResponseTimeoutMillis = 3000L;

    @Value("${rgs.device.manipulator.telemetry-stale-millis:2000}")
    private long manipulatorTelemetryStaleMillis = 2000L;

    @Value("${rgs.device.manipulator.hatch-closed-di-bit:0}")
    private long manipulatorHatchClosedDiBit = 0L;

    @Value("${rgs.device.manipulator.hatch-open-di-bit:1}")
    private long manipulatorHatchOpenDiBit = 1L;

    @Value("${rgs.device.barcode-scanner.port:}")
    private String barcodeScannerPort = "";

    @Value("${rgs.device.barcode-scanner.baud-rate:9600}")
    private long barcodeScannerBaudRate = 9600L;

    @Value("${rgs.device.barcode-scanner.read-timeout-millis:5000}")
    private long barcodeScannerReadTimeoutMillis = 5000L;

    @Value("${rgs.device.barcode-scanner.max-code-length:4096}")
    private long barcodeScannerMaxCodeLength = 4096L;

    @Value("${rgs.device.gdrm.port:COM3}")
    private String gdrmPort = "COM3";

    @Value("${rgs.device.gdrm.initial-baud-rate:9600}")
    private long gdrmInitialBaudRate = 9600L;

    @Value("${rgs.device.gdrm.read-timeout-millis:250}")
    private long gdrmReadTimeoutMillis = 250L;

    @Value("${rgs.device.gdrm.response-timeout-millis:1200}")
    private long gdrmResponseTimeoutMillis = 1200L;

    @Value("${rgs.device.scales.port:}")
    private String scalesPort = "";

    @Value("${rgs.device.shp.port:COM5}")
    private String shpPort = "COM5";

    @Value("${rgs.device.shp.baud-rate:9600}")
    private long shpBaudRate = 9600L;

    @Value("${rgs.device.shp.read-timeout-millis:250}")
    private long shpReadTimeoutMillis = 250L;

    @Value("${rgs.device.shp.response-timeout-millis:1200}")
    private long shpResponseTimeoutMillis = 1200L;

    @Value("${rgs.device.gamma-pro.host:127.0.0.1}")
    private String gammaProHost = "127.0.0.1";

    @Value("${rgs.device.gamma-pro.port:37569}")
    private long gammaProPort = 37569L;

    @Value("${rgs.device.gamma-pro.connect-timeout-millis:2000}")
    private long gammaProConnectTimeoutMillis = 2000L;

    @Value("${rgs.device.gamma-pro.read-timeout-millis:250}")
    private long gammaProReadTimeoutMillis = 250L;

    @Value("${rgs.device.gamma-pro.response-timeout-millis:3000}")
    private long gammaProResponseTimeoutMillis = 3000L;

    @Value("${rgs.device.gamma-pro.charset:windows-1251}")
    private String gammaProCharset = "windows-1251";

    /** Регистрирует начальные значения после внедрения Spring properties. */
    @PostConstruct
    void initializeDefaults() {
        registerDefault(CAMERA_IP, SettingType.STRING, cameraIp);
        registerDefault(ROBOPLC_IP, SettingType.STRING, roboplcIp);
        registerDefault(NICOLE_IP, SettingType.STRING, nicoleIp);
        registerDefault(BOSON_IP, SettingType.STRING, bosonIp);
        registerDefault(MANIPULATOR_HOST, SettingType.STRING, manipulatorHost);
        registerDefault(MANIPULATOR_COMMAND_PORT, SettingType.LONG, manipulatorCommandPort);
        registerDefault(MANIPULATOR_FEEDBACK_PORT, SettingType.LONG, manipulatorFeedbackPort);
        registerDefault(MANIPULATOR_CONNECT_TIMEOUT_MILLIS, SettingType.LONG, manipulatorConnectTimeoutMillis);
        registerDefault(MANIPULATOR_READ_TIMEOUT_MILLIS, SettingType.LONG, manipulatorReadTimeoutMillis);
        registerDefault(MANIPULATOR_RESPONSE_TIMEOUT_MILLIS, SettingType.LONG, manipulatorResponseTimeoutMillis);
        registerDefault(MANIPULATOR_TELEMETRY_STALE_MILLIS, SettingType.LONG, manipulatorTelemetryStaleMillis);
        registerDefault(MANIPULATOR_HATCH_CLOSED_DI_BIT, SettingType.LONG, manipulatorHatchClosedDiBit);
        registerDefault(MANIPULATOR_HATCH_OPEN_DI_BIT, SettingType.LONG, manipulatorHatchOpenDiBit);
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
        registerDefault(GDRM_PORT, SettingType.STRING, gdrmPort);
        registerDefault(
                GDRM_INITIAL_BAUD_RATE,
                SettingType.LONG,
                gdrmInitialBaudRate
        );
        registerDefault(
                GDRM_READ_TIMEOUT_MILLIS,
                SettingType.LONG,
                gdrmReadTimeoutMillis
        );
        registerDefault(
                GDRM_RESPONSE_TIMEOUT_MILLIS,
                SettingType.LONG,
                gdrmResponseTimeoutMillis
        );
        registerDefault(SCALES_PORT, SettingType.STRING, scalesPort);
        registerDefault(SHP_PORT, SettingType.STRING, shpPort);
        registerDefault(SHP_BAUD_RATE, SettingType.LONG, shpBaudRate);
        registerDefault(
                SHP_READ_TIMEOUT_MILLIS,
                SettingType.LONG,
                shpReadTimeoutMillis
        );
        registerDefault(
                SHP_RESPONSE_TIMEOUT_MILLIS,
                SettingType.LONG,
                shpResponseTimeoutMillis
        );
        registerDefault(GAMMA_PRO_HOST, SettingType.STRING, gammaProHost);
        registerDefault(GAMMA_PRO_PORT, SettingType.LONG, gammaProPort);
        registerDefault(
                GAMMA_PRO_CONNECT_TIMEOUT_MILLIS,
                SettingType.LONG,
                gammaProConnectTimeoutMillis
        );
        registerDefault(
                GAMMA_PRO_READ_TIMEOUT_MILLIS,
                SettingType.LONG,
                gammaProReadTimeoutMillis
        );
        registerDefault(
                GAMMA_PRO_RESPONSE_TIMEOUT_MILLIS,
                SettingType.LONG,
                gammaProResponseTimeoutMillis
        );
        registerDefault(
                GAMMA_PRO_CHARSET,
                SettingType.STRING,
                gammaProCharset
        );
        registerDefault(PING_TIMEOUT_MILLIS, SettingType.LONG, 3000L);
        log.info("Initialized {} application settings", settingValues.size());
    }

    /**
     * Добавляет настройку, не перезаписывая уже загруженное значение.
     *
     * @param code строковый идентификатор
     * @param type допустимый тип значения
     * @param value начальное значение
     */
    public void registerDefault(String code, SettingType type, Object value) {
        validate(code, type, value);
        settingTypes.putIfAbsent(code, type.getCode());
        settingValues.putIfAbsent(code, value);
    }

    /**
     * @param code идентификатор настройки
     * @return числовой код типа
     */
    public int getType(String code) {
        Integer type = settingTypes.get(code);
        if (type == null) {
            throw new NoSuchElementException("Unknown setting: " + code);
        }
        return type;
    }

    /**
     * @param code идентификатор настройки
     * @return текущее значение
     */
    public Object getValue(String code) {
        Object value = settingValues.get(code);
        if (value == null) {
            throw new NoSuchElementException("Unknown setting: " + code);
        }
        return value;
    }

    /**
     * Возвращает значение с проверяемым приведением типа.
     *
     * @param code идентификатор настройки
     * @param valueClass ожидаемый Java-класс
     * @param <T> тип результата
     * @return текущее значение
     */
    public <T> T getValue(String code, Class<T> valueClass) {
        return valueClass.cast(getValue(code));
    }

    /**
     * Изменяет существующую настройку после проверки её зарегистрированного типа.
     *
     * @param code идентификатор настройки
     * @param value новое значение
     */
    public void setValue(String code, Object value) {
        SettingType type = SettingType.fromCode(getType(code));
        validate(code, type, value);
        settingValues.put(code, value);
        log.info("Setting {} updated (type={})", code, type);
    }

    /**
     * Загружает записи в формате будущей таблицы БД.
     *
     * @param records записи настроек
     */
    public void load(Collection<SettingRecord> records) {
        for (SettingRecord record : records) {
            SettingType type = SettingType.fromCode(record.type());
            Object value = record.value();
            validate(record.stringId(), type, value);
            settingTypes.put(record.stringId(), type.getCode());
            settingValues.put(record.stringId(), value);
        }
        log.info("Loaded {} settings from persistent storage", records.size());
    }

    /**
     * Формирует отсортированный снимок для сохранения в БД.
     *
     * @return записи с отдельными типизированными колонками
     */
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

    /**
     * Проверяет идентификатор и соответствие значения объявленному типу.
     *
     * @param code идентификатор настройки
     * @param type ожидаемый тип
     * @param value проверяемое значение
     */
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
