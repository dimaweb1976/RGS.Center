package com.rgs.center.web.wizardnew;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.DeviceHealth;
import com.rgs.center.dashboard.model.EquipmentSnapshot;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.measurement.workflow.MeasurementWorkflowSnapshot;
import com.rgs.center.measurement.workflow.ProductionMeasurementOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Locale;

/** Reads published snapshots only; this service does not poll or command hardware. */
@Service
public class WizardNewShellModelService {

    private final DashboardSnapshotService dashboardService;
    private final ProductionMeasurementOrchestrator orchestrator;

    public WizardNewShellModelService(
            DashboardSnapshotService dashboardService,
            ProductionMeasurementOrchestrator orchestrator
    ) {
        this.dashboardService = dashboardService;
        this.orchestrator = orchestrator;
    }

    public void addTo(Model model, String title, String section) {
        model.addAttribute("pageTitle", title);
        model.addAttribute("activeSection", section);
        model.addAttribute("wizardNewShell", snapshot());
    }

    public WizardNewShellView snapshot() {
        EquipmentSnapshot equipment = dashboardService.snapshot().equipment();
        MeasurementWorkflowSnapshot workflow = orchestrator.snapshot();
        String workflowDetail = workflow.runId() == null
                ? "No active run" : "Run " + workflow.runId();
        return new WizardNewShellView(
                workflow.state().name().replace('_', ' '),
                workflowDetail,
                List.of(
                        unavailable("PLC", "No published PLC snapshot"),
                        device("Gamma-PRO", equipment.gammaPro().health()),
                        shield(equipment),
                        highVoltage(equipment),
                        unavailable("MCA", "No independent MCA snapshot"),
                        cooling(equipment)
                )
        );
    }

    private static WizardNewShellView.StatusItem unavailable(String label, String detail) {
        return new WizardNewShellView.StatusItem(label, "No data", detail, "unknown");
    }

    private static WizardNewShellView.StatusItem device(String label, DeviceHealth health) {
        String value;
        if (!health.enabled()) value = "Disabled";
        else if (health.connection() == ConnectionState.OFFLINE) value = "Offline";
        else if (health.connection() == ConnectionState.CONNECTING) value = "Connecting";
        else if (health.technical() == TechnicalState.ALERT) value = "Alert";
        else if (health.stale()) value = "Stale";
        else if (health.technical() == TechnicalState.NO_DATA) value = "No data";
        else if (health.technical() == TechnicalState.BUSY) value = "Busy";
        else if (health.connection() == ConnectionState.ONLINE
                && health.technical() == TechnicalState.READY) value = "Online";
        else value = "No data";
        String tone = switch (value) {
            case "Online" -> "ready";
            case "Offline", "Alert", "Stale" -> "alert";
            case "Connecting", "Busy" -> "warning";
            default -> "unknown";
        };
        String detail = "Connection: " + health.connection().name()
                + "; technical: " + health.technical().name();
        return new WizardNewShellView.StatusItem(label, value, detail, tone);
    }

    private static WizardNewShellView.StatusItem shield(EquipmentSnapshot equipment) {
        DeviceHealth health = equipment.manipulator().health();
        if (!health.enabled() || health.connection() != ConnectionState.ONLINE
                || health.stale() || health.technical() == TechnicalState.ALERT) {
            return device("Shield", health);
        }
        var state = equipment.manipulator().hatch().state();
        if (state.name().equals("UNKNOWN")) {
            return unavailable("Shield", "Hatch position has not been observed");
        }
        String value = state.name().toLowerCase(Locale.ROOT);
        value = Character.toUpperCase(value.charAt(0)) + value.substring(1);
        String tone = state.name().equals("CLOSED") ? "ready" : "warning";
        if (state.name().equals("INVALID")) tone = "alert";
        return new WizardNewShellView.StatusItem(
                "Shield", value, "Manipulator hatch feedback", tone
        );
    }

    private static WizardNewShellView.StatusItem highVoltage(EquipmentSnapshot equipment) {
        Boolean requested = equipment.boson().highVoltageRequested();
        if (requested == null || equipment.boson().health().stale()) {
            return unavailable("Detector HV", "Gamma-PRO / BOSON HV request is unknown");
        }
        String value = requested ? "On requested" : "Off requested";
        return new WizardNewShellView.StatusItem(
                "Detector HV", value,
                "HV request only; measured voltage is not available here", "unknown"
        );
    }

    private static WizardNewShellView.StatusItem cooling(EquipmentSnapshot equipment) {
        DeviceHealth health = equipment.nicole().health();
        if (!health.enabled() || health.connection() != ConnectionState.ONLINE
                || health.stale() || equipment.nicole().cryocoolerStarted() == null
                || health.technical() == TechnicalState.NO_DATA) {
            return device("Cooling system", health);
        }
        if (health.technical() == TechnicalState.ALERT) {
            return new WizardNewShellView.StatusItem(
                    "Cooling system", "Alert", "Nicole technical status is ALERT", "alert"
            );
        }
        boolean started = equipment.nicole().cryocoolerStarted();
        return new WizardNewShellView.StatusItem(
                "Cooling system", started ? "Started" : "Stopped",
                "Nicole cryocooler status from protocol PONG",
                started ? "ready" : "alert"
        );
    }
}
