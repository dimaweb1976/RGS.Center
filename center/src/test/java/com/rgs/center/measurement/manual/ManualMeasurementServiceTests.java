package com.rgs.center.measurement.manual;

import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import com.rgs.center.measurement.parameters.SampleGeometry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ManualMeasurementServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-14T08:00:00Z");
    private final FakeGateway gateway = new FakeGateway();
    private final ManualMeasurementRepository repository = mock(ManualMeasurementRepository.class);
    private final MeasurementParametersService parametersService = mock(MeasurementParametersService.class);
    private MeasurementExecutionCoordinator executionCoordinator;
    private ManualMeasurementService service;

    @BeforeEach
    void setUp() {
        when(parametersService.current()).thenReturn(parameters());
        executionCoordinator = new MeasurementExecutionCoordinator();
        service = new ManualMeasurementService(
                gateway, repository, parametersService, executionCoordinator,
                Clock.fixed(NOW, ZoneOffset.UTC),
                Executors.newSingleThreadExecutor()
        );
    }

    @AfterEach
    void shutdown() {
        service.shutdown();
    }

    @Test
    void preparesSampleAcquiresSpectrumAndPersistsTerminalResult() throws Exception {
        gateway.barcode = " SAMPLE-42 ";
        gateway.weight = new BigDecimal("125.50");
        gateway.spectrum = "spectrum-42";

        assertThat(service.scanBarcode()).isEqualTo("SAMPLE-42");
        assertThat(service.readWeight()).isEqualByComparingTo("125.50");
        service.startSpectrumAcquisition();

        assertThat(awaitTerminal().state()).isEqualTo(ManualMeasurementState.COMPLETED);
        ArgumentCaptor<ManualMeasurementResult> result =
                ArgumentCaptor.forClass(ManualMeasurementResult.class);
        verify(repository).insert(result.capture());
        assertThat(result.getValue().sampleCode()).isEqualTo("SAMPLE-42");
        assertThat(result.getValue().grossMassGrams()).isEqualByComparingTo("125.50");
        assertThat(result.getValue().netMassGrams()).isEqualByComparingTo("100.50");
        assertThat(result.getValue().spectrumReference()).isEqualTo("spectrum-42");
        assertThat(gateway.preconditionsChecked).isTrue();
    }

    @Test
    void refusesAcquisitionWhenProtocolPreconditionsFail() {
        gateway.preconditionFailure = new IllegalStateException("Hatch is open");
        service.setSampleCode("SAMPLE-1");
        gateway.weight = new BigDecimal("50");
        service.readWeight();

        assertThatThrownBy(service::startSpectrumAcquisition)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Hatch is open");
        assertThat(service.snapshot().state()).isEqualTo(ManualMeasurementState.IDLE);
        assertThat(service.snapshot().lastError()).isEqualTo("Hatch is open");
        verify(repository, never()).insert(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void safeStopCancelsAcquisitionAndPersistsCancellation() throws Exception {
        service.setSampleCode("SAMPLE-2");
        gateway.weight = new BigDecimal("50");
        service.readWeight();
        gateway.blockAcquisition = true;
        service.startSpectrumAcquisition();
        assertThat(gateway.acquisitionEntered.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(service.stopSpectrumAcquisition()).isTrue();

        assertThat(service.snapshot().state()).isEqualTo(ManualMeasurementState.CANCELLED);
        assertThat(gateway.stopCalled).isTrue();
        ArgumentCaptor<ManualMeasurementResult> result =
                ArgumentCaptor.forClass(ManualMeasurementResult.class);
        verify(repository).insert(result.capture());
        assertThat(result.getValue().status()).isEqualTo(ManualMeasurementState.CANCELLED);
    }

    @Test
    void blocksManualDeviceCommandsWhileAutomaticWorkflowIsActive() {
        executionCoordinator.acquire(MeasurementExecutionCoordinator.Owner.AUTOMATIC);

        assertThatThrownBy(service::scanBarcode)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reserved by AUTOMATIC");
        assertThat(gateway.scanCalled).isFalse();
    }

    private ManualMeasurementSnapshot awaitTerminal() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            ManualMeasurementSnapshot snapshot = service.snapshot();
            if (snapshot.state() == ManualMeasurementState.COMPLETED
                    || snapshot.state() == ManualMeasurementState.FAILED
                    || snapshot.state() == ManualMeasurementState.CANCELLED) {
                return snapshot;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Manual measurement did not finish");
    }

    private static MeasurementParameters parameters() {
        return new MeasurementParameters(
                1, 300, SampleGeometry.BOTTLE, 25, 1, NOW
        );
    }

    private static final class FakeGateway implements ManualMeasurementDeviceGateway {
        private String barcode = "CODE";
        private BigDecimal weight = BigDecimal.ONE;
        private String spectrum = "spectrum";
        private RuntimeException preconditionFailure;
        private boolean preconditionsChecked;
        private boolean scanCalled;
        private boolean blockAcquisition;
        private boolean stopCalled;
        private final CountDownLatch acquisitionEntered = new CountDownLatch(1);
        private final CountDownLatch acquisitionRelease = new CountDownLatch(1);

        @Override public String scanBarcode() { scanCalled = true; return barcode; }
        @Override public BigDecimal readWeight() { return weight; }
        @Override public void openHatch() { }
        @Override public void closeHatch() { }
        @Override public void enableHighVoltage() { }
        @Override public void disableHighVoltage() { }
        @Override public void verifySpectrumPreconditions() {
            preconditionsChecked = true;
            if (preconditionFailure != null) throw preconditionFailure;
        }
        @Override public String acquireSpectrum(MeasurementParameters parameters) {
            acquisitionEntered.countDown();
            if (blockAcquisition) {
                try {
                    acquisitionRelease.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted", exception);
                }
            }
            return spectrum;
        }
        @Override public void stopSpectrumAcquisition() {
            stopCalled = true;
            acquisitionRelease.countDown();
        }
    }
}
