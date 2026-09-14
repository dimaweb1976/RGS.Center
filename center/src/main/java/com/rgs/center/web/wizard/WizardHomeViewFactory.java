package com.rgs.center.web.wizard;

import com.rgs.center.dashboard.model.BosonEquipmentSnapshot;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.EquipmentSnapshot;
import com.rgs.center.dashboard.model.ManipulatorEquipmentSnapshot;
import com.rgs.center.dashboard.model.NicoleEquipmentSnapshot;
import com.rgs.center.dashboard.model.ShpEquipmentSnapshot;
import com.rgs.center.dashboard.model.TechnicalState;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Преобразует общий dashboard snapshot в компактную модель главного экрана. */
@Component
public class WizardHomeViewFactory {

    /**
     * Формирует подписи без подмены запрошенного HV фактически измеренным.
     *
     * @param snapshot единый снимок оборудования
     * @return модель header и панели предупреждений
     */
    public WizardHomeView create(DashboardSnapshot snapshot) {
        EquipmentSnapshot equipment = snapshot.equipment();
        return new WizardHomeView(
                equipment.collectedAt(),
                statusItems(equipment),
                alerts(equipment.healthSummary())
        );
    }

    private static List<WizardStatusItem> statusItems(
            EquipmentSnapshot equipment
    ) {
        ManipulatorEquipmentSnapshot manipulator = equipment.manipulator();
        NicoleEquipmentSnapshot nicole = equipment.nicole();
        ShpEquipmentSnapshot shp = equipment.shp();
        BosonEquipmentSnapshot boson = equipment.boson();

        return List.of(
                item(
                        "robot",
                        "Robot",
                        label(manipulator.health().technical()),
                        connectionDetail(manipulator.health()),
                        manipulator.health()
                ),
                item(
                        "hv",
                        "HV request",
                        highVoltageValue(boson),
                        "Gamma-PRO / BOSON",
                        boson.health()
                ),
                item(
                        "ln2",
                        "LN₂ level",
                        integerValue(nicole.currentNitrogenLevel()),
                        nullableDetail(
                                "Pressure",
                                nicole.currentNitrogenPressure()
                        ),
                        nicole.health()
                ),
                item(
                        "temperature",
                        "SHP temperature",
                        decimalValue(shp.ambientTemperatureCelsius(), " °C"),
                        "Ambient sensor",
                        shp.health()
                ),
                item(
                        "current",
                        "Leakage current",
                        decimalValue(shp.leakageCurrent(), ""),
                        "Detector monitoring",
                        shp.health()
                ),
                item(
                        "shield",
                        "Lead shield",
                        label(manipulator.hatch().state()),
                        "Spectrometer hatch",
                        manipulator.health()
                )
        );
    }

    private static WizardStatusItem item(
            String code,
            String label,
            String value,
            String detail,
            DeviceHealth health
    ) {
        return new WizardStatusItem(
                code,
                label,
                value,
                detail,
                indicatorState(health)
        );
    }

    private static List<WizardAlertView> alerts(List<DeviceHealth> health) {
        List<WizardAlertView> result = new ArrayList<>();
        for (DeviceHealth device : health) {
            if (!device.enabled()) {
                continue;
            }
            boolean critical = device.connection() == ConnectionState.OFFLINE
                    || device.technical() == TechnicalState.ALERT;
            boolean warning = device.connection() == ConnectionState.CONNECTING
                    || device.technical() == TechnicalState.NO_DATA
                    || device.stale();
            if (!critical && !warning) {
                continue;
            }
            result.add(new WizardAlertView(
                    device.name(),
                    alertMessage(device),
                    critical
            ));
        }
        return List.copyOf(result);
    }

    private static String alertMessage(DeviceHealth health) {
        if (!health.reasons().isEmpty()) {
            return String.join("; ", health.reasons());
        }
        if (!health.lastError().isBlank()) {
            return health.lastError();
        }
        if (health.connection() == ConnectionState.OFFLINE) {
            return "Connection is offline";
        }
        if (health.connection() == ConnectionState.CONNECTING) {
            return "Connection is being established";
        }
        if (health.stale()) {
            return "Last data is stale";
        }
        return "Technical data is not available";
    }

    private static WizardIndicatorState indicatorState(DeviceHealth health) {
        if (!health.enabled()
                || health.technical() == TechnicalState.DISABLED) {
            return WizardIndicatorState.NO_DATA;
        }
        if (health.connection() == ConnectionState.OFFLINE
                || health.technical() == TechnicalState.ALERT
                || health.stale()) {
            return WizardIndicatorState.ALERT;
        }
        if (health.connection() == ConnectionState.CONNECTING
                || health.technical() == TechnicalState.BUSY) {
            return WizardIndicatorState.WARNING;
        }
        if (health.connection() == ConnectionState.ONLINE
                && health.technical() == TechnicalState.READY) {
            return WizardIndicatorState.READY;
        }
        return WizardIndicatorState.NO_DATA;
    }

    private static String highVoltageValue(BosonEquipmentSnapshot boson) {
        if (boson.highVoltageRequested() == null) {
            return "No data";
        }
        if (!boson.highVoltageRequested()) {
            return "Off requested";
        }
        return boson.requestedHighVoltage() == null
                ? "On requested"
                : boson.requestedHighVoltage() + " V requested";
    }

    private static String integerValue(Integer value) {
        return value == null ? "No data" : Integer.toString(value);
    }

    private static String nullableDetail(String label, Integer value) {
        return value == null ? label + ": no data" : label + ": " + value;
    }

    private static String decimalValue(Double value, String suffix) {
        return value == null
                ? "No data"
                : String.format(Locale.ROOT, "%.3f%s", value, suffix);
    }

    private static String connectionDetail(DeviceHealth health) {
        return "Connection: " + label(health.connection());
    }

    private static String label(Enum<?> value) {
        return value.name().replace('_', ' ');
    }
}
