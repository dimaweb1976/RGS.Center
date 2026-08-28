package com.rgs.center.nicole;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * Полный неизменяемый снимок 13 слов ответа ACK_PING/PONG контроллера NICOLE.
 *
 * @param resetCause причина последнего сброса контроллера
 * @param ethernetUp признак активного Ethernet-интерфейса
 * @param dhcpLeased признак получения адреса по DHCP
 * @param cryoControllerCommunication связь с контроллером криоохладителя
 * @param cryocoolerStarted криоохладитель запущен
 * @param advancedMode расширенный режим управления
 * @param currentNitrogenLevel текущее значение уровня азота
 * @param currentNitrogenPressure текущее значение давления азота
 * @param lowerLevelVoltage напряжение нижнего уровня
 * @param upperLevelVoltage напряжение верхнего уровня
 * @param nitrogenLevelFrequency частота канала уровня азота
 * @param controllerTime дата и время, переданные контроллером
 * @param coolerErrorCode код ошибки криоохладителя
 * @param warningFlags битовая маска предупреждений
 * @param receivedAt время получения снимка RGSCenter
 */
public record NicoleSnapshot(
        int resetCause,
        boolean ethernetUp,
        boolean dhcpLeased,
        boolean cryoControllerCommunication,
        boolean cryocoolerStarted,
        boolean advancedMode,
        int currentNitrogenLevel,
        int currentNitrogenPressure,
        int lowerLevelVoltage,
        int upperLevelVoltage,
        long nitrogenLevelFrequency,
        LocalDateTime controllerTime,
        int coolerErrorCode,
        int warningFlags,
        Instant receivedAt
) {
}
