package com.rgs.center.barcodescanner;

/** Current application-level state of a barcode scan request. */
public enum BarcodeScannerOperationState {
    IDLE,
    SCANNING,
    SUCCESS,
    TIMEOUT,
    CANCELLED,
    ERROR
}
