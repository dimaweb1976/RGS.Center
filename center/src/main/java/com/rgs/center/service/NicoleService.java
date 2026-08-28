package com.rgs.center.service;

import com.rgs.center.connection.TcpDeviceConnection;
import com.rgs.center.nicole.NicoleConnectionStatus;
import com.rgs.center.nicole.NicoleProtocol;
import com.rgs.center.nicole.NicoleReadinessStatus;
import com.rgs.center.nicole.NicoleSnapshot;
import com.rgs.center.nicole.NicoleStatusView;
import com.rgs.center.nicole.NicoleTechnicalStatusChangedEvent;
import com.rgs.center.nicole.NicoleTechnicalStatusEvaluator;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.DelimitedFrameDecoder;
import com.rgs.center.peripheral.PeripheralWorkerConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Самостоятельный TCP-сервис контроля и чтения состояния NICOLE.
 *
 * <p>Доступность определяется не ICMP, а полным прикладным обменом: сервис
 * отправляет PING, проверяет ACK_PING/PONG, CRC16 и все 13 слов ответа. Только
 * после успешного разбора витрина получает {@code pinged=true}.</p>
 */
@Service
public class NicoleService extends AbstractQueuedDeviceService {

    private static final Logger log = LoggerFactory.getLogger(NicoleService.class);

    private final SettingsService settingsService;
    private final CurrentDataService currentDataService;
    private final DeviceViewService deviceViewService;
    private final NicoleTechnicalStatusEvaluator technicalStatusEvaluator;
    private final ApplicationEventPublisher eventPublisher;
    private final ReentrantLock protocolLock = new ReentrantLock();

    /** Последний корректный PONG сохраняется и при последующей потере связи. */
    private volatile NicoleSnapshot lastSnapshot;
    /** Техническая готовность не подменяет состояние TCP-связи. */
    private volatile NicoleReadinessStatus technicalStatus =
            NicoleReadinessStatus.NO_DATA;

    /**
     * @param settingsService адрес, порт и тайм-ауты NICOLE
     * @param currentDataService общая витрина текущих данных
     * @param deviceViewService модель общей таблицы устройств
     * @param technicalStatusEvaluator правила READY/ALERT
     * @param eventPublisher передача изменения статуса автоматическому сценарию
     */
    public NicoleService(
            SettingsService settingsService,
            CurrentDataService currentDataService,
            DeviceViewService deviceViewService,
            NicoleTechnicalStatusEvaluator technicalStatusEvaluator,
            ApplicationEventPublisher eventPublisher
    ) {
        super(
                DeviceCode.NICOLE,
                createConnection(settingsService),
                new PeripheralWorkerConfiguration(
                        8192,
                        32,
                        32,
                        256,
                        NicoleProtocol.MAX_TRANSPORT_FRAME_LENGTH,
                        true
                ),
                new DelimitedFrameDecoder(
                        NicoleProtocol.START,
                        NicoleProtocol.STOP,
                        NicoleProtocol.MAX_TRANSPORT_FRAME_LENGTH
                )
        );
        this.settingsService = settingsService;
        this.currentDataService = currentDataService;
        this.deviceViewService = deviceViewService;
        this.technicalStatusEvaluator = technicalStatusEvaluator;
        this.eventPublisher = eventPublisher;
    }

    /** Отправляет протокольный PING и публикует разобранный PONG в витрине. */
    @Scheduled(
            fixedDelayString = "${rgs.device.nicole.poll-interval-millis:10000}",
            initialDelayString = "${rgs.device.nicole.poll-initial-delay-millis:0}"
    )
    public void poll() {
        if (!isEnabled()) {
            return;
        }
        if (!protocolLock.tryLock()) {
            log.trace("NICOLE poll skipped: another protocol exchange is active");
            return;
        }
        try {
            pollLocked();
        } finally {
            protocolLock.unlock();
        }
    }

    /** @return последнее состояние NICOLE для общей таблицы устройств */
    public Device getNicole() {
        return deviceViewService.getDevice(DeviceCode.NICOLE);
    }

    /** @return последний корректный PONG и доступность текущего обмена */
    public NicoleStatusView statusView() {
        if (!isEnabled()) {
            return NicoleStatusView.of(
                    lastSnapshot,
                    NicoleConnectionStatus.DISABLED,
                    NicoleReadinessStatus.DISABLED
            );
        }
        boolean pinged = currentDataService.getValue(
                DeviceCode.NICOLE,
                CurrentDataService.PINGED,
                Boolean.class
        );
        return NicoleStatusView.of(
                lastSnapshot,
                pinged
                        ? NicoleConnectionStatus.ONLINE
                        : NicoleConnectionStatus.OFFLINE,
                pinged
                        ? technicalStatus
                        : NicoleReadinessStatus.NO_DATA
        );
    }

    /** Выполняет один сериализованный обмен и обновляет признаки доступности. */
    private void pollLocked() {
        boolean previousPinged = currentDataService.getValue(
                DeviceCode.NICOLE,
                CurrentDataService.PINGED,
                Boolean.class
        );
        long previousCheck = currentDataService.getValue(
                DeviceCode.NICOLE,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Long.class
        );
        currentDataService.setValue(
                DeviceCode.NICOLE,
                CurrentDataService.INTERACTION_LASTDATE_IN,
                Instant.now().getEpochSecond()
        );

        try {
            byte[] response = exchangeQueued(
                    NicoleProtocol.pingFrame(),
                    Duration.ofMillis(responseTimeoutMillis())
            );
            NicoleSnapshot snapshot = NicoleProtocol.decodePong(response);
            lastSnapshot = snapshot;
            updateTechnicalStatus(snapshot);
            currentDataService.setValue(
                    DeviceCode.NICOLE,
                    CurrentDataService.PINGED,
                    true
            );
            currentDataService.recordDataSuccess(DeviceCode.NICOLE, snapshot);
            if (!previousPinged) {
                log.info("NICOLE protocol PING/PONG succeeded");
            } else {
                log.debug("NICOLE protocol PING/PONG remains available");
            }
        } catch (RuntimeException exception) {
            currentDataService.setValue(
                    DeviceCode.NICOLE,
                    CurrentDataService.PINGED,
                    false
            );
            currentDataService.recordDataFailure(
                    DeviceCode.NICOLE,
                    errorMessage(exception)
            );
            if (previousCheck == 0 || previousPinged) {
                log.warn(
                        "NICOLE protocol PING/PONG failed: {}",
                        errorMessage(exception)
                );
            } else {
                log.debug(
                        "NICOLE protocol PING/PONG remains unavailable: {}",
                        errorMessage(exception)
                );
            }
        }
    }

    /** Публикует ровно одно событие на фактическое изменение READY/ALERT. */
    private void updateTechnicalStatus(NicoleSnapshot snapshot) {
        NicoleReadinessStatus previous = technicalStatus;
        NicoleReadinessStatus current =
                technicalStatusEvaluator.evaluate(snapshot);
        technicalStatus = current;
        if (previous == current) {
            return;
        }
        var reasons = current == NicoleReadinessStatus.ALERT
                ? technicalStatusEvaluator.alertReasons(snapshot)
                : java.util.List.<String>of();
        log.info(
                "NICOLE technical status changed: {} -> {}{}",
                previous,
                current,
                reasons.isEmpty() ? "" : ", reasons=" + reasons
        );
        try {
            eventPublisher.publishEvent(new NicoleTechnicalStatusChangedEvent(
                    previous,
                    current,
                    snapshot,
                    reasons,
                    Instant.now()
            ));
        } catch (RuntimeException exception) {
            // Ошибка потребителя аварии не превращает валидный PONG в OFFLINE.
            log.error(
                    "Cannot deliver NICOLE technical status event",
                    exception
            );
        }
    }

    private long responseTimeoutMillis() {
        return settingsService.getValue(
                SettingsService.NICOLE_RESPONSE_TIMEOUT_MILLIS,
                Long.class
        );
    }

    private static String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    /** Создаёт TCP-транспорт, читающий актуальные настройки при переподключении. */
    private static TcpDeviceConnection createConnection(
            SettingsService settingsService
    ) {
        return new TcpDeviceConnection(
                "tcp:nicole",
                () -> settingsService.getValue(
                        SettingsService.NICOLE_IP,
                        String.class
                ),
                () -> Math.toIntExact(settingsService.getValue(
                        SettingsService.NICOLE_PORT,
                        Long.class
                )),
                () -> Math.toIntExact(settingsService.getValue(
                        SettingsService.NICOLE_CONNECT_TIMEOUT_MILLIS,
                        Long.class
                )),
                () -> Math.toIntExact(settingsService.getValue(
                        SettingsService.NICOLE_READ_TIMEOUT_MILLIS,
                        Long.class
                ))
        );
    }
}
