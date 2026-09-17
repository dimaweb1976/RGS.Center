package com.rgs.center.web.wizard;

import com.rgs.center.device.settings.DeviceSetting;
import com.rgs.center.device.settings.DeviceSettingAudit;
import com.rgs.center.device.settings.DeviceSettingsApplyTracker;
import com.rgs.center.device.settings.DeviceSettingsService;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.SettingType;
import com.rgs.center.service.SettingsService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Adds labels and validation metadata to persisted device settings. */
@Component
public class WizardSettingsViewFactory {

    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final DeviceSettingsService settings;
    private final DeviceSettingsApplyTracker applyTracker;

    public WizardSettingsViewFactory(
            DeviceSettingsService settings,
            DeviceSettingsApplyTracker applyTracker
    ) {
        this.settings = settings;
        this.applyTracker = applyTracker;
    }

    public WizardSettingsView.Overview overview() {
        Map<String, List<DeviceSetting>> grouped = settings.findAll().stream()
                .collect(Collectors.groupingBy(
                        DeviceSetting::deviceCode,
                        Collectors.toList()
                ));
        return new WizardSettingsView.Overview(Arrays.stream(DeviceCode.values())
                .filter(device -> grouped.containsKey(device.getCode()))
                .map(device -> summary(device, grouped.get(device.getCode())))
                .toList());
    }

    public WizardSettingsView.Detail detail(String deviceCode, boolean editable) {
        DeviceCode device = Arrays.stream(DeviceCode.values())
                .filter(value -> value.getCode().equals(deviceCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown device settings: " + deviceCode
                ));
        List<WizardSettingsView.Field> fields = settings.findByDevice(deviceCode)
                .stream()
                .map(this::field)
                .toList();
        List<WizardSettingsView.AuditEntry> audit = settings
                .findRecentAudit(deviceCode, 50)
                .stream()
                .map(this::audit)
                .toList();
        return new WizardSettingsView.Detail(
                device.getCode(),
                device.getDisplayName(),
                description(device),
                fields,
                audit,
                applyTracker.isPending(deviceCode),
                editable
        );
    }

    private WizardSettingsView.DeviceSummary summary(
            DeviceCode device,
            List<DeviceSetting> values
    ) {
        DeviceSetting latest = values.stream()
                .filter(value -> !Instant.EPOCH.equals(value.updatedAt()))
                .max(Comparator.comparing(DeviceSetting::updatedAt))
                .orElse(null);
        return new WizardSettingsView.DeviceSummary(
                device.getCode(),
                device.getDisplayName(),
                description(device),
                values.size(),
                latest == null ? "Defaults" : TIME.format(latest.updatedAt()),
                latest == null || latest.updatedByLogin() == null
                        ? "deployment" : latest.updatedByLogin(),
                applyTracker.isPending(device.getCode())
        );
    }

    private WizardSettingsView.Field field(DeviceSetting setting) {
        Limits limits = limits(setting);
        return new WizardSettingsView.Field(
                setting.stringId(),
                label(setting.stringId(), setting.type()),
                setting.type().name(),
                setting.value(),
                setting.version(),
                unit(setting.stringId()),
                hint(setting.stringId(), setting.type()),
                limits.minimum(),
                limits.maximum(),
                Instant.EPOCH.equals(setting.updatedAt())
                        ? "Not saved yet" : TIME.format(setting.updatedAt()),
                setting.updatedByLogin() == null
                        ? "deployment" : setting.updatedByLogin(),
                !Instant.EPOCH.equals(setting.updatedAt())
        );
    }

    private WizardSettingsView.AuditEntry audit(DeviceSettingAudit value) {
        return new WizardSettingsView.AuditEntry(
                label(value.stringId(), value.type()),
                value.oldValue() == null || value.oldValue().isBlank()
                        ? "Not stored" : value.oldValue(),
                value.newValue(),
                TIME.format(value.changedAt()),
                value.changedByLogin() == null
                        ? Long.toString(value.changedBy()) : value.changedByLogin()
        );
    }

    private static Limits limits(DeviceSetting setting) {
        if (setting.type() != SettingType.LONG) {
            return new Limits(null, null);
        }
        String id = setting.stringId();
        if (id.endsWith("-port") || id.endsWith(".port")) {
            return new Limits(1L, 65_535L);
        }
        if (id.endsWith("baud-rate")) {
            return new Limits(300L, 2_000_000L);
        }
        if (id.contains("timeout-millis")) {
            return new Limits(1L, 300_000L);
        }
        return new Limits(null, null);
    }

    private static String label(String id, SettingType type) {
        return switch (lastPart(id)) {
            case "ip" -> "IP address";
            case "host" -> "Host address";
            case "port" -> type == SettingType.STRING ? "COM port" : "TCP port";
            case "command-port" -> "Dobot command port";
            case "feedback-port" -> "Dobot feedback port";
            case "feedback-only" -> "Feedback-only - telemetry only";
            case "initial-baud-rate" -> "Initial baud rate";
            case "baud-rate" -> "Baud rate";
            case "connect-timeout-millis" -> "Connection timeout";
            case "read-timeout-millis" -> "Read timeout";
            case "response-timeout-millis" -> "Response timeout";
            case "charset" -> "Character encoding";
            default -> title(lastPart(id));
        };
    }

    private static String unit(String id) {
        if (id.contains("timeout-millis")) return "ms";
        if (id.endsWith("baud-rate")) return "baud";
        return "";
    }

    private static String hint(String id, SettingType type) {
        if (id.equals(SettingsService.MANIPULATOR_FEEDBACK_ONLY)) {
            return "Connect only to feedback. Commands and analogue polling disabled. Save, then Apply to device.";
        }
        if (id.endsWith(".ip")) return "IPv4 address, for example 192.168.1.13";
        if (id.endsWith(".port") && type == SettingType.STRING) {
            return "Windows serial port, for example COM7";
        }
        if (id.endsWith(".port")) return "TCP port from 1 to 65535";
        if (id.endsWith("-port")) return "TCP port from 1 to 65535";
        if (id.contains("timeout-millis")) return "Allowed range: 1-300000 ms";
        if (id.endsWith("baud-rate")) return "Allowed range: 300-2000000 baud";
        if (id.endsWith(".charset")) return "Java-supported encoding, for example windows-1251";
        return "Saved in PostgreSQL and used after applying the connection settings.";
    }

    private static String description(DeviceCode device) {
        return switch (device) {
            case CAMERA -> "Machine-vision camera endpoint";
            case LENOVO_PC -> "Operator workstation reachability";
            case NICOLE -> "Cooling controller TCP protocol";
            case BOSON -> "Detector network address";
            case ROBOTIC_MANIPULATOR -> "Dobot command and feedback channels";
            case BARCODE_SCANNER -> "Barcode reader serial connection";
            case GDRM -> "GDRM serial connection";
            case SCALES -> "RADWAG scales serial connection";
            case SHP -> "State-of-health preamplifier connection";
            case GAMMA_PRO -> "Gamma-PRO TCP command channel";
        };
    }

    private static String lastPart(String id) {
        int index = id.lastIndexOf('.');
        return index < 0 ? id : id.substring(index + 1);
    }

    private static String title(String value) {
        return Arrays.stream(value.split("-"))
                .map(capitalize())
                .collect(Collectors.joining(" "));
    }

    private static Function<String, String> capitalize() {
        return value -> value.isEmpty()
                ? value
                : Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private record Limits(Long minimum, Long maximum) {
    }
}
