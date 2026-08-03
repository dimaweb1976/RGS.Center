package com.rgs.center.peripheral;

/** Логический получатель входящего кадра после классификации. */
public enum IncomingFrameRoute {
    COMMAND_RESPONSE,
    TELEMETRY,
    ALERT,
    UNKNOWN
}
