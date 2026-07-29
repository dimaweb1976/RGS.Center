package com.rgs.center.service;

/** Абстракция проверки сетевой доступности для production и тестов. */
@FunctionalInterface
public interface ReachabilityChecker {

    /**
     * @param address IP-адрес или имя узла
     * @param timeoutMillis максимальное время проверки
     * @return доступен ли узел
     */
    boolean isReachable(String address, int timeoutMillis);
}
