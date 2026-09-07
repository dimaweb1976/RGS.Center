package com.rgs.center.barcodescanner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarcodeScannerDataSuffixTests {

    @Test
    void parsesConfiguredValuesCaseInsensitively() {
        assertEquals(
                BarcodeScannerDataSuffix.AUTO,
                BarcodeScannerDataSuffix.fromConfiguration(" auto ")
        );
        assertEquals(
                BarcodeScannerDataSuffix.CRLF,
                BarcodeScannerDataSuffix.fromConfiguration("crlf")
        );
        assertEquals(
                BarcodeScannerDataSuffix.NONE,
                BarcodeScannerDataSuffix.fromConfiguration("NONE")
        );
    }

    @Test
    void rejectsUnsupportedConfiguredValueWithAllowedValuesInMessage() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> BarcodeScannerDataSuffix.fromConfiguration("TAB")
        );

        assertTrue(exception.getMessage().contains("AUTO, CR, LF, CRLF or NONE"));
    }
}
