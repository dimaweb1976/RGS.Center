package com.rgs.center.service;

import com.rgs.center.connection.SerialDeviceConnection;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;
import com.rgs.center.peripheral.PeripheralWorkerState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

/**
 * COM-адаптер сканера штрих-кодов на базе общего peripheral worker.
 */
@Component
public class JSerialCommBarcodeScannerPort
        extends AbstractQueuedDeviceService
        implements BarcodeScannerPort {

    private static final Logger log =
            LoggerFactory.getLogger(JSerialCommBarcodeScannerPort.class);

    /** Витрина состояния, используемая страницей {@code /devices}. */
    private final CurrentDataService currentDataService;

    /**
     * Создаёт постоянное COM-подключение, которое менеджер запускает вместе
     * с остальными устройствами.
     *
     * @param settingsService источник параметров COM
     */
    public JSerialCommBarcodeScannerPort(
            SettingsService settingsService,
            CurrentDataService currentDataService
    ) {
        super(
                DeviceCode.BARCODE_SCANNER,
                createConnection(settingsService),
                createConfiguration(settingsService)
        );
        this.currentDataService = currentDataService;
    }

    /**
     * Обновляет время последней проверки и доступность виртуального COM-порта.
     *
     * <p>Сканер является пассивным устройством и не поддерживает отдельную
     * ping-команду. Поэтому доступность определяется по состоянию его COM-worker.
     * При потере соединения сервис выполняет очередную попытку открытия порта.</p>
     */
    @Scheduled(
            fixedDelayString =
                    "${rgs.device.barcode-scanner.status-interval-millis:10000}",
            initialDelayString =
                    "${rgs.device.barcode-scanner.status-initial-delay-millis:0}"
    )
    public void refreshConnectionStatus() {
        long checkedAt = Instant.now().getEpochSecond();
        boolean connected;
        try {
            if (communicationSnapshot().state()
                    != PeripheralWorkerState.RUNNING) {
                startCommunication();
            }
            connected = communicationSnapshot().state()
                    == PeripheralWorkerState.RUNNING;
        } catch (RuntimeException exception) {
            connected = false;
            log.debug(
                    "Barcode scanner connection check failed",
                    exception
            );
        }

        currentDataService.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                checkedAt
        );
        currentDataService.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.PINGED,
                connected
        );
    }

    /**
     * Получает очередной строковый кадр через уже запущенный RX-worker.
     *
     * <p>Если начальное подключение не удалось, общий базовый класс выполняет
     * повторную попытку перед ожиданием кадра. COM остаётся открыт до остановки
     * приложения.</p>
     *
     * @param portName системное имя COM-порта
     * @param baudRate скорость обмена
     * @param timeoutMillis максимальное время ожидания кода
     * @param maxCodeLength максимальная длина штрих-кода
     * @return прочитанный штрих-код
     */
    @Override
    public String readCode(
            String portName,
            int baudRate,
            int timeoutMillis,
            int maxCodeLength
    ) {
        log.debug(
                "Creating barcode scanner session on port {}, baudRate={}, timeout={} ms, maxLength={}",
                portName,
                baudRate,
                timeoutMillis,
                maxCodeLength
        );
        try {
            byte[] frame = awaitQueuedFrame(
                    Duration.ofMillis(timeoutMillis)
            ).orElseThrow(() -> new BarcodeScannerException(
                    "Barcode read timed out on port " + portName
            ));
            log.debug(
                    "Barcode scanner frame received on port {}, bytes={}",
                    portName,
                    frame.length
            );
            return new String(frame, StandardCharsets.UTF_8).trim();
        } catch (BarcodeScannerException exception) {
            log.debug(
                    "Barcode scanner session ended with an application error on port {}",
                    portName
            );
            throw exception;
        } catch (RuntimeException exception) {
            log.debug(
                    "Barcode scanner transport error on port {}",
                    portName,
                    exception
            );
            throw new BarcodeScannerException(
                    "Cannot read barcode from port " + portName,
                    exception
            );
        }
    }

    /**
     * @param settingsService источник параметров COM
     * @return конкретное COM-подключение общего типа
     */
    private static SerialDeviceConnection createConnection(
            SettingsService settingsService
    ) {
        return new SerialDeviceConnection(
                settingsService.getValue(
                        SettingsService.BARCODE_SCANNER_PORT,
                        String.class
                ),
                Math.toIntExact(settingsService.getValue(
                        SettingsService.BARCODE_SCANNER_BAUD_RATE,
                        Long.class
                )),
                Math.toIntExact(settingsService.getValue(
                        SettingsService.BARCODE_SCANNER_READ_TIMEOUT_MILLIS,
                        Long.class
                ))
        );
    }

    /**
     * @param settingsService источник ограничения длины штрих-кода
     * @return конфигурация очередей сканера
     */
    private static PeripheralWorkerConfiguration createConfiguration(
            SettingsService settingsService
    ) {
        return PeripheralWorkerConfiguration.lineDevice(
                Math.toIntExact(settingsService.getValue(
                        SettingsService.BARCODE_SCANNER_MAX_CODE_LENGTH,
                        Long.class
                ))
        );
    }
}
