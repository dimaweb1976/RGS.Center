package com.rgs.center.service;

import org.springframework.stereotype.Component;

import java.net.InetAddress;

@Component
public class InetReachabilityChecker implements ReachabilityChecker {

    @Override
    public boolean isReachable(String address, int timeoutMillis) {
        try {
            return InetAddress.getByName(address).isReachable(timeoutMillis);
        } catch (Exception ignored) {
            return false;
        }
    }
}
