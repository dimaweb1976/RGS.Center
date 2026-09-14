package com.rgs.center.barcodescanner;

import com.rgs.center.objects.DeviceCode;
import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import com.rgs.center.service.CurrentDataService;
import com.rgs.center.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void invokesAsyncCompletionOnlyAfterReadHasFinished() throws Exception {
        settings.setValue(SettingsService.BARCODE_SCANNER_PORT, "COM7");
        CountDownLatch allowReadToFinish = new CountDownLatch(1);
        MeasurementExecutionCoordinator coordinator =
                new MeasurementExecutionCoordinator();
        coordinator.acquire(MeasurementExecutionCoordinator.Owner.SERVICE);
        BarcodeScannerService service = serviceWith(
                (port, baud, timeout, maxLength) -> {
                    try {
                        assertTrue(allowReadToFinish.await(2, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new BarcodeScannerException("read interrupted", exception);
                    }
                    return "ABC-123";
                }
        );

        assertTrue(service.startRead(() -> coordinator.release(
                MeasurementExecutionCoordinator.Owner.SERVICE
        )));
        assertEquals(
                MeasurementExecutionCoordinator.Owner.SERVICE,
                coordinator.owner()
        );

        allowReadToFinish.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (coordinator.owner() != MeasurementExecutionCoordinator.Owner.NONE
                && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        assertEquals(
                MeasurementExecutionCoordinator.Owner.NONE,
                coordinator.owner()
        );
        service.closeExecutor();
    }

    private BarcodeScannerService serviceWith(BarcodeScannerPort port) {
        BarcodeScannerGetDataAdapter adapter =
                new BarcodeScannerGetDataAdapter(settings, port, currentData);
        return new BarcodeScannerService(adapter);
    }
}
