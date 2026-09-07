package com.rgs.center.barcodescanner;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Application facade and asynchronous launcher for one barcode scan. */
@Service
public class BarcodeScannerService {

    private final BarcodeScannerGetDataAdapter getDataAdapter;
    private final JSerialCommBarcodeScannerPort scannerPort;
    private final ExecutorService scanExecutor =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(
                        runnable,
                        "barcode-scanner-operation"
                );
                thread.setDaemon(true);
                return thread;
            });
    private Future<?> activeRead;

    @Autowired
    public BarcodeScannerService(
            BarcodeScannerGetDataAdapter getDataAdapter,
            JSerialCommBarcodeScannerPort scannerPort
    ) {
        this.getDataAdapter = getDataAdapter;
        this.scannerPort = scannerPort;
    }

    /** Constructor retained for isolated facade unit tests. */
    BarcodeScannerService(BarcodeScannerGetDataAdapter getDataAdapter) {
        this.getDataAdapter = getDataAdapter;
        scannerPort = null;
    }

    /** Synchronously reads one barcode for the automatic RGS scenario. */
    public String readBarcode() {
        return getDataAdapter.getData();
    }

    /** Starts a service-page scan without holding the HTTP request open. */
    public synchronized boolean startRead() {
        requireProductionPort();
        if (isReadActive()) {
            return false;
        }
        activeRead = scanExecutor.submit(() -> {
            try {
                readBarcode();
            } catch (BarcodeScannerException ignored) {
                // The port snapshot and CurrentDataService expose the result.
            }
        });
        return true;
    }

    /** Interrupts the wait; the port still sends trigger-off and LED-off. */
    public synchronized boolean cancelRead() {
        if (!isReadActive()) {
            return false;
        }
        return activeRead.cancel(true);
    }

    public synchronized boolean isReadActive() {
        return activeRead != null && !activeRead.isDone();
    }

    public BarcodeScannerSnapshot snapshot() {
        requireProductionPort();
        return scannerPort.snapshot();
    }

    @PreDestroy
    void closeExecutor() {
        scanExecutor.shutdownNow();
    }

    private void requireProductionPort() {
        if (scannerPort == null) {
            throw new IllegalStateException(
                    "Barcode scanner status is unavailable in an isolated test"
            );
        }
    }
}
