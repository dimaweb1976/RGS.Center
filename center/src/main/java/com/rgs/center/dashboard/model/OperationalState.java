package com.rgs.center.dashboard.model;

/** Нормализованное состояние выполняемой устройством операции. */
public enum OperationalState {
    DISABLED,
    IDLE,
    PREPARING,
    RUNNING,
    COMPLETED,
    FAILED,
    UNKNOWN
}
