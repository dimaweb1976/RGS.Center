package com.rgs.center.barcodescanner;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private Runnable activeReadCompletion = () -> { };

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
        return startRead(() -> { });
    }

    /**
     * Starts a scan and invokes the completion callback after the scanner has
     * finished both reading and its trigger/illumination cleanup sequence.
     */
    public synchronized boolean startRead(Runnable onCompletion) {
        if (isReadActive()) {
            return false;
        }
        Runnable completion = runOnce(onCompletion);
        activeReadCompletion = completion;
        try {
            activeRead = scanExecutor.submit(() -> {
                try {
                    readBarcode();
                } catch (BarcodeScannerException ignored) {
                    // The port snapshot and CurrentDataService expose the result.
                } finally {
                    completion.run();
                }
            });
        } catch (RuntimeException exception) {
            activeReadCompletion = () -> { };
            throw exception;
        }
        return true;
    }

    /** Interrupts the wait; the port still sends trigger-off and LED-off. */
    public synchronized boolean cancelRead() {
        if (!isReadActive()) {
            return false;
        }
        boolean cancelled = activeRead.cancel(true);
        if (cancelled) {
            try {
                // Runs after a currently executing read has left its cleanup block.
                scanExecutor.execute(activeReadCompletion);
            } catch (RejectedExecutionException exception) {
                activeReadCompletion.run();
            }
        }
        return cancelled;
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

    private static Runnable runOnce(Runnable callback) {
        Runnable required = callback == null ? () -> { } : callback;
        AtomicBoolean invoked = new AtomicBoolean();
        return () -> {
            if (invoked.compareAndSet(false, true)) {
                required.run();
            }
        };
    }
}
