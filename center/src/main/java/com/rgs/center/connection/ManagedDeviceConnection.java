package com.rgs.center.connection;

import com.rgs.center.objects.DeviceCode;
import com.rgs.center.peripheral.PeripheralWorkerSnapshot;

/**
 * Устройство, соединение и очереди которого управляются приложением.
 */
public interface ManagedDeviceConnection {

    /** @return постоянный код устройства */
    DeviceCode deviceCode();

    /** @return whether startup, polling and reconnects are allowed */
    default boolean isEnabled() {
        return true;
    }

    /** Открывает подключение и запускает RX/TX-worker. */
    void startCommunication();

    /** Штатно останавливает worker и закрывает подключение. */
    void stopCommunication();

    /** @return диагностический снимок очередей и подключения */
    PeripheralWorkerSnapshot communicationSnapshot();
}
