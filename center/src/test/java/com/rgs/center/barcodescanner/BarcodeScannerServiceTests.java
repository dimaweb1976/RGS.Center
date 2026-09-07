package com.rgs.center.barcodescanner;

import com.rgs.center.objects.DeviceCode;
import com.rgs.center.service.CurrentDataService;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BarcodeScannerServiceTests {

    private SettingsService settings;
    private CurrentDataService currentData;

    @BeforeEach
    void setUp() {
        settings = new SettingsService();
        ReflectionTestUtils.invokeMethod(settings, "initializeDefaults");
        currentData = new CurrentDataService();
        ReflectionTestUtils.invokeMethod(currentData, "initializeDevices");
    }

    @Test
    void rejectsReadWhenComPortIsNotConfigured() {
        BarcodeScannerService service = serviceWith(
                (port, baud, timeout, maxLength) -> "unused"
        );

        assertThrows(
                BarcodeScannerException.class,
                service::readBarcode
        );
    }

    @Test
    void readsOneCodeUsingConfiguredSerialParameters() {
        settings.setValue(SettingsService.BARCODE_SCANNER_PORT, "COM7");
        StringBuilder invocation = new StringBuilder();
        BarcodeScannerService service = serviceWith(
                (port, baud, timeout, maxLength) -> {
                    invocation.append(port)
                            .append(':')
                            .append(baud)
                            .append(':')
                            .append(timeout)
                            .append(':')
                            .append(maxLength);
                    return "ABC-123";
                }
        );

        assertEquals("ABC-123", service.readBarcode());
        assertEquals("COM7:9600:5000:4096", invocation.toString());
        assertEquals(
                "ABC-123",
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.LAST_DATA,
                        String.class
                )
        );
        assertEquals(
                true,
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.DATA_AVAILABLE,
                        Boolean.class
                )
        );
        assertEquals(
                true,
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.PINGED,
                        Boolean.class
                )
        );
    }

    @Test
    void recordsReadFailureInCurrentData() {
        settings.setValue(SettingsService.BARCODE_SCANNER_PORT, "COM7");
        currentData.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.PINGED,
                true
        );
        BarcodeScannerService service = serviceWith(
                (port, baud, timeout, maxLength) -> {
                    throw new BarcodeScannerException("scanner unavailable");
                }
        );

        assertThrows(BarcodeScannerException.class, service::readBarcode);
        assertEquals(
                "scanner unavailable",
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.LAST_ERROR,
                        String.class
                )
        );
        assertEquals(
                false,
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.DATA_AVAILABLE,
                        Boolean.class
                )
        );
        assertEquals(
                false,
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.PINGED,
                        Boolean.class
                )
        );
    }

    @Test
    void noReadDoesNotMarkResponsiveScannerOffline() {
        settings.setValue(SettingsService.BARCODE_SCANNER_PORT, "COM8");
        currentData.setValue(
                DeviceCode.BARCODE_SCANNER,
                CurrentDataService.PINGED,
                true
        );
        BarcodeScannerService service = serviceWith(
                (port, baud, timeout, maxLength) -> {
                    throw new BarcodeScannerNoReadException("no barcode");
                }
        );

        assertThrows(BarcodeScannerNoReadException.class, service::readBarcode);
        assertEquals(
                true,
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.PINGED,
                        Boolean.class
                )
        );
        assertEquals(
                false,
                currentData.getValue(
                        DeviceCode.BARCODE_SCANNER,
                        CurrentDataService.DATA_AVAILABLE,
                        Boolean.class
                )
        );
    }

    private BarcodeScannerService serviceWith(BarcodeScannerPort port) {
        BarcodeScannerGetDataAdapter adapter =
                new BarcodeScannerGetDataAdapter(settings, port, currentData);
        return new BarcodeScannerService(adapter);
    }
}
