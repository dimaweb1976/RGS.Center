package com.rgs.center.connection;

/**
 * Заменяемая реализация проверки доступности LAN-устройства.
 */
@FunctionalInterface
public interface LanReachabilityProbe {

    /**
     * @param address IP-адрес или DNS-имя устройства
     * @param timeoutMillis максимальное время проверки
     * @return {@code true}, если устройство ответило
     */
    boolean isReachable(String address, int timeoutMillis);
}
