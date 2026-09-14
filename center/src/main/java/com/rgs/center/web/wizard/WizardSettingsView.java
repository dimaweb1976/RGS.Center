package com.rgs.center.web.wizard;

import java.util.List;

/** Server-rendered view models for the stage-10 settings workspace. */
public final class WizardSettingsView {

    private WizardSettingsView() {
    }

    public record Overview(List<DeviceSummary> devices) {
        public Overview {
            devices = List.copyOf(devices);
        }
    }

    public record DeviceSummary(
            String code,
            String name,
            String description,
            int settingCount,
            String lastChanged,
            String lastChangedBy,
            boolean restartRequired
    ) {
    }

    public record Detail(
            String code,
            String name,
            String description,
            List<Field> fields,
            List<AuditEntry> audit,
            boolean restartRequired,
            boolean editable
    ) {
        public Detail {
            fields = List.copyOf(fields);
            audit = List.copyOf(audit);
        }
    }

    public record Field(
            String id,
            String label,
            String type,
            Object value,
            long version,
            String unit,
            String hint,
            Long minimum,
            Long maximum,
            String updatedAt,
            String updatedBy,
            boolean persisted
    ) {
    }

    public record AuditEntry(
            String setting,
            String oldValue,
            String newValue,
            String changedAt,
            String changedBy
    ) {
    }
}
