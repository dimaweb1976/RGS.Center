package com.rgs.center.service;

import com.rgs.center.objects.DeviceCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Прикладной getData-адаптер сканера штрих-кодов.
 *
 * <p>Адаптер получает транспортные параметры из настроек, вызывает COM-порт
 * и фиксирует результат либо ошибку в витрине текущих данных.</p>
 */
@Component
public class BarcodeScannerGetDataAdapter implements GetDataAdapter<String> {

    private static final Logger log =
            LoggerFactory.getLogger(BarcodeScannerGetDataAdapter.class);

    /** Источник параметров COM-порта. */
    private final SettingsService settingsService;

    /** Изолированный транспортный адаптер сканера. */
    private final BarcodeScannerPort scannerPort;

    /** Общая витрина текущего состояния оборудования. */
    private final CurrentDataService currentDataService;

    /**
     * @param settingsService сервис настроек
     * @param scannerPort транспортный порт сканера
     * @param currentDataService витрина текущих данных
     */
    public BarcodeScannerGetDataAdapter(
            SettingsService settingsService,
            BarcodeScannerPort scannerPort,
            CurrentDataService currentDataService
    ) {
        this.settingsService = settingsService;
        this.scannerPort = scannerPort;
        this.currentDataService = currentDataService;
    }

    /**
     * @return код сканера в витрине
     */
    @Override
    public DeviceCode deviceCode() {
        return DeviceCode.BARCODE_SCANNER;
    }

    /**
     * Синхронно получает один кадр из RX-очереди и обновляет витрину.
     *
     * @return прочитанный штрих-код
     */
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
            log.warn("Barcode scanner read rejected: COM port is not configured");
            recordFailure(exception);
            throw exception;
        }

        log.info("Barcode scanner read started on port {}", portName);
        currentDataService.setValue(
                deviceCode(),
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Instant.now().getEpochSecond()
        );

        try {
            // Чтение использует постоянный RX-worker управляемого COM-подключения.
            String barcode = scannerPort.readCode(
                    portName,
                    toInt(SettingsService.BARCODE_SCANNER_BAUD_RATE),
                    toInt(SettingsService.BARCODE_SCANNER_READ_TIMEOUT_MILLIS),
                    toInt(SettingsService.BARCODE_SCANNER_MAX_CODE_LENGTH)
            );
            currentDataService.setValue(
                    deviceCode(),
                    CurrentDataService.PINGED,
                    true
            );
            currentDataService.recordDataSuccess(deviceCode(), barcode);
            log.info(
                    "Barcode scanner read completed on port {}, characters={}",
                    portName,
                    barcode.length()
            );
            return barcode;
        } catch (RuntimeException exception) {
            // Ошибка сохраняется для диагностики и передаётся вызывающему коду.
            recordFailure(exception);
            log.error(
                    "Barcode scanner read failed on port {}",
                    portName,
                    exception
            );
            throw exception;
        }
    }

    /**
     * Получает целочисленный параметр, проверяя переполнение long → int.
     *
     * @param settingCode код настройки
     * @return значение типа int
     */
    private int toInt(String settingCode) {
        return Math.toIntExact(settingsService.getValue(
                settingCode,
                Long.class
        ));
    }

    /**
     * Записывает диагностическое сообщение в витрину.
     *
     * @param exception исходная ошибка чтения
     */
    private void recordFailure(RuntimeException exception) {
        currentDataService.setValue(
                deviceCode(),
                CurrentDataService.PINGED,
                false
        );
        currentDataService.recordDataFailure(
                deviceCode(),
                exception.getMessage()
        );
    }
}
