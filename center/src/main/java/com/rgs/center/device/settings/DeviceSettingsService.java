package com.rgs.center.device.settings;

import com.rgs.center.objects.SettingRecord;
import com.rgs.center.service.SettingsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Transactional boundary for browser reads and optimistic-lock updates. */
@Service
public class DeviceSettingsService {

    private final DeviceSettingsRepository repository;
    private final DeviceSettingCatalog catalog;
    private final SettingsService settingsService;

    public DeviceSettingsService(
            DeviceSettingsRepository repository,
            DeviceSettingCatalog catalog,
            SettingsService settingsService
    ) {
        this.repository = repository;
        this.catalog = catalog;
        this.settingsService = settingsService;
    }

    @Transactional(readOnly = true)
    public List<DeviceSetting> findAll() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<DeviceSetting> findByDevice(String deviceCode) {
        catalog.requireDevice(deviceCode);
        return repository.findByDevice(deviceCode);
    }

    /**
     * Updates all submitted fields in one transaction. If one version is stale,
     * the exception rolls back every preceding UPDATE in the same request.
     */
    @Transactional
    public List<DeviceSetting> updateDevice(
            String deviceCode,
            List<Update> updates,
            long administratorId
    ) {
        catalog.requireDevice(deviceCode);
        if (updates == null || updates.isEmpty()) {
            throw new IllegalArgumentException("At least one setting is required");
        }

        Set<String> uniqueIds = new HashSet<>();
        for (Update update : updates) {
            if (update == null || update.stringId() == null) {
                throw new IllegalArgumentException("Setting id is required");
            }
            if (!uniqueIds.add(update.stringId())) {
                throw new IllegalArgumentException("Duplicate setting: " + update.stringId());
            }
            DeviceSettingCatalog.Definition definition = catalog.require(update.stringId());
            if (!definition.deviceCode().equals(deviceCode)) {
                throw new IllegalArgumentException(
                        update.stringId() + " does not belong to " + deviceCode
                );
            }
            if (update.version() < 0) {
                throw new IllegalArgumentException("Version must not be negative");
            }
            Object value = catalog.convertAndValidate(definition, update.value());
            int changed = repository.update(
                    deviceCode,
                    definition.stringId(),
                    definition.type(),
                    value,
                    update.version(),
                    administratorId
            );
            if (changed != 1) {
                throw new DeviceSettingsConflictException(update.stringId());
            }
        }

        List<DeviceSetting> saved = repository.findByDevice(deviceCode);
        settingsService.load(saved.stream()
                .map(DeviceSetting::toSettingRecord)
                .toList());
        return saved;
    }

    /** Loads and validates persisted values before device communication starts. */
    @Transactional(readOnly = true)
    public int loadPersistedSettings() {
        List<DeviceSetting> stored = repository.findAll();
        stored.forEach(catalog::validateStored);
        List<SettingRecord> records = stored.stream()
                .map(DeviceSetting::toSettingRecord)
                .toList();
        settingsService.load(records);
        return records.size();
    }

    public record Update(String stringId, Object value, long version) {
    }
}
