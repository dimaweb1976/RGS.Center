package com.rgs.center.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;

/** Проверяет доступность средствами {@link InetAddress#isReachable(int)}. */
@Component
public class InetReachabilityChecker implements ReachabilityChecker {

    private static final Logger log =
            LoggerFactory.getLogger(InetReachabilityChecker.class);

    /**
     * @param address IP-адрес или имя узла
     * @param timeoutMillis тайм-аут проверки
     * @return {@code false} также при ошибке DNS/сети
     */
    @Override
    public boolean isReachable(String address, int timeoutMillis) {
        try {
            return InetAddress.getByName(address).isReachable(timeoutMillis);
        } catch (Exception exception) {
            log.debug(
                    "Reachability check failed for address {}",
                    address,
                    exception
            );
            return false;
        }
    }
}
