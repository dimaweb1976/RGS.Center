package com.rgs.center.barcodescanner;

import java.util.Locale;

/** Configured framing of decoded barcode data received from the scanner. */
public enum BarcodeScannerDataSuffix {
    AUTO,
    CR,
    LF,
    CRLF,
    NONE;

    /** Parses the application property and reports all supported values. */
    public static BarcodeScannerDataSuffix fromConfiguration(String value) {
        if (value == null || value.isBlank()) {
            throw invalid(value);
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(value);
        }
    }

    private static IllegalArgumentException invalid(String value) {
        return new IllegalArgumentException(
                "Unsupported barcode scanner data suffix '" + value
                        + "'; expected AUTO, CR, LF, CRLF or NONE"
        );
    }
}
