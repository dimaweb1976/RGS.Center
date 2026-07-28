package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class BarcodeScannerGetDataAdapter implements GetDataAdapter<String> {

    private final SettingsService settingsService;
    private final BarcodeScannerPort scannerPort;
    private final CurrentDataService currentDataService;

    public BarcodeScannerGetDataAdapter(
            SettingsService settingsService,
            BarcodeScannerPort scannerPort,
            CurrentDataService currentDataService
    ) {
        this.settingsService = settingsService;
        this.scannerPort = scannerPort;
        this.currentDataService = currentDataService;
    }

    @Override
    public DeviceCode deviceCode() {
        return DeviceCode.BARCODE_SCANNER;
    }

    @Override
    public synchronized String getData() {
        String portName = settingsService.getValue(
                SettingsService.BARCODE_SCANNER_PORT,
                String.class
        );
        if (portName.isBlank()) {
            BarcodeScannerException exception = new BarcodeScannerException(
                    "Barcode scanner COM port is not configured"
            );
            recordFailure(exception);
            throw exception;
        }

        currentDataService.setValue(
                deviceCode(),
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Instant.now().getEpochSecond()
        );

        try {
            String barcode = scannerPort.readCode(
                    portName,
                    toInt(SettingsService.BARCODE_SCANNER_BAUD_RATE),
                    toInt(SettingsService.BARCODE_SCANNER_READ_TIMEOUT_MILLIS),
                    toInt(SettingsService.BARCODE_SCANNER_MAX_CODE_LENGTH)
            );
            currentDataService.recordDataSuccess(deviceCode(), barcode);
            return barcode;
        } catch (RuntimeException exception) {
            recordFailure(exception);
            throw exception;
        }
    }

    private int toInt(String settingCode) {
        return Math.toIntExact(settingsService.getValue(
                settingCode,
                Long.class
        ));
    }

    private void recordFailure(RuntimeException exception) {
        currentDataService.recordDataFailure(
                deviceCode(),
                exception.getMessage()
        );
    }
}
