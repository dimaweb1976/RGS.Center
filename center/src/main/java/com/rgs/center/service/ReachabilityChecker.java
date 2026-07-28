package com.rgs.center.service;

@FunctionalInterface
public interface ReachabilityChecker {

    boolean isReachable(String address, int timeoutMillis);
}
