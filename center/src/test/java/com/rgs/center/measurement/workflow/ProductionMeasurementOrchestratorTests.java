package com.rgs.center.measurement.workflow;

import com.rgs.center.measurement.ScenarioStatus;
import com.rgs.center.measurement.ScenarioStatusService;
import com.rgs.center.measurement.parameters.MeasurementParameters;
import com.rgs.center.measurement.parameters.MeasurementParametersService;
import com.rgs.center.measurement.parameters.SampleGeometry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionMeasurementOrchestratorTests {

    private ProductionMeasurementOrchestrator orchestrator;

    @AfterEach
    void stopExecutors() {
        if (orchestrator != null) orchestrator.shutdown();
    }

    @Test
    void processesEveryObjectInRequiredOrderAndStoresResults() throws Exception {
        FakeGateway gateway = new FakeGateway();
        ScenarioStatusService scenario = new ScenarioStatusService();
        orchestrator = orchestrator(gateway, scenario, 2);

        orchestrator.start(List.of(
                new MeasurementObjectRequest(1, 10.5, 20.5),
                new MeasurementObjectRequest(2, 30.5, 40.5)
        ));

        MeasurementWorkflowSnapshot snapshot = awaitTerminal();
        assertThat(snapshot.state()).isEqualTo(MeasurementWorkflowState.COMPLETED);
        assertThat(snapshot.objects()).hasSize(2).allSatisfy(object -> {
            assertThat(object.status()).isEqualTo("COMPLETED");
            assertThat(object.barcode()).startsWith("CODE-");
            assertThat(object.grossMassGrams()).isEqualByComparingTo("125.5");
            assertThat(object.netMassGrams()).isEqualByComparingTo("100.5");
            assertThat(object.spectrumReference()).startsWith("spectrum-");
            assertThat(object.lastCompletedStep())
                    .isEqualTo(MeasurementWorkflowStep.RETURN_OBJECT);
        });
        assertThat(gateway.calls).containsExactly(
                "preflight", "pick-1", "barcode-position-1", "barcode-1",
                "scales-position-1", "weight-1", "spectrometer-position-1",
                "spectrum-1", "spectrometer-remove-1", "return-1",
                "pick-2", "barcode-position-2", "barcode-2",
                "scales-position-2", "weight-2", "spectrometer-position-2",
                "spectrum-2", "spectrometer-remove-2", "return-2"
        );
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.NORMAL);
    }

    @Test
    void stopsBeforeFirstCommandWhenPreflightFails() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.preflight = new WorkflowPreflightReport(List.of("SHP is offline"));
        ScenarioStatusService scenario = new ScenarioStatusService();
        orchestrator = orchestrator(gateway, scenario, 2);

        orchestrator.start(List.of(new MeasurementObjectRequest(1, 1, 2)));

        MeasurementWorkflowSnapshot snapshot = awaitTerminal();
        assertThat(snapshot.state()).isEqualTo(MeasurementWorkflowState.FAILED);
        assertThat(snapshot.lastError()).contains("SHP is offline");
        assertThat(gateway.calls).containsExactly("preflight");
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.EMERGENCY);
    }

    @Test
    void retriesBarcodeReadButDoesNotRepeatMotionCommands() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.barcodeFailuresRemaining = 1;
        orchestrator = orchestrator(gateway, new ScenarioStatusService(), 2);

        orchestrator.start(List.of(new MeasurementObjectRequest(1, 1, 2)));

        assertThat(awaitTerminal().state())
                .isEqualTo(MeasurementWorkflowState.COMPLETED);
        assertThat(Collections.frequency(gateway.calls, "pick-1")).isOne();
        assertThat(Collections.frequency(gateway.calls, "barcode-1")).isEqualTo(2);
        assertThat(Collections.frequency(gateway.calls, "spectrum-1")).isOne();
    }

    @Test
    void cancelsBlockingDeviceOperationAndEndsInCancelledState() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.blockBarcode = true;
        ScenarioStatusService scenario = new ScenarioStatusService();
        orchestrator = orchestrator(gateway, scenario, 2);
        orchestrator.start(List.of(new MeasurementObjectRequest(1, 1, 2)));
        assertThat(gateway.barcodeEntered.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(orchestrator.cancel()).isTrue();

        MeasurementWorkflowSnapshot snapshot = awaitTerminal();
        assertThat(snapshot.state()).isEqualTo(MeasurementWorkflowState.CANCELLED);
        assertThat(snapshot.objects().getFirst().status()).isEqualTo("CANCELLED");
        assertThat(gateway.cancelCalled).isTrue();
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.NORMAL);
    }

    @Test
    void pausesOnlyAfterActiveOperationAndResumesWithNextStep() throws Exception {
        FakeGateway gateway = new FakeGateway();
        gateway.blockBarcode = true;
        ScenarioStatusService scenario = new ScenarioStatusService();
        orchestrator = orchestrator(gateway, scenario, 2);
        orchestrator.start(List.of(new MeasurementObjectRequest(1, 1, 2)));
        assertThat(gateway.barcodeEntered.await(2, TimeUnit.SECONDS)).isTrue();

        assertThat(orchestrator.pause()).isTrue();
        assertThat(orchestrator.snapshot().state())
                .isEqualTo(MeasurementWorkflowState.PAUSING);
        gateway.barcodeRelease.countDown();
        awaitState(MeasurementWorkflowState.PAUSED);

        assertThat(gateway.calls).doesNotContain("scales-position-1");
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.PAUSED);
        assertThat(orchestrator.resume()).isTrue();

        assertThat(awaitTerminal().state())
                .isEqualTo(MeasurementWorkflowState.COMPLETED);
        assertThat(Collections.frequency(gateway.calls, "barcode-1")).isOne();
        assertThat(gateway.calls).contains("scales-position-1", "return-1");
        assertThat(scenario.currentStatus()).isEqualTo(ScenarioStatus.NORMAL);
    }

    private ProductionMeasurementOrchestrator orchestrator(
            FakeGateway gateway,
            ScenarioStatusService scenario,
            int maxAttempts
    ) {
        MeasurementParametersService parametersService =
                mock(MeasurementParametersService.class);
        when(parametersService.current()).thenReturn(new MeasurementParameters(
                1, 300, SampleGeometry.BOTTLE, 25, 0, Instant.EPOCH
        ));
        MeasurementWorkflowConfiguration configuration =
                new MeasurementWorkflowConfiguration();
        configuration.setMaxAttempts(maxAttempts);
        configuration.setOperationTimeout(Duration.ofSeconds(2));
        configuration.setSpectrumOverrun(Duration.ofSeconds(1));
        return new ProductionMeasurementOrchestrator(
                gateway, parametersService, scenario, configuration
        );
    }

    private MeasurementWorkflowSnapshot awaitTerminal() throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            MeasurementWorkflowSnapshot snapshot = orchestrator.snapshot();
            if (snapshot.state() == MeasurementWorkflowState.COMPLETED
                    || snapshot.state() == MeasurementWorkflowState.FAILED
                    || snapshot.state() == MeasurementWorkflowState.CANCELLED) {
                return snapshot;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("Workflow did not reach a terminal state");
    }

    private MeasurementWorkflowSnapshot awaitState(MeasurementWorkflowState expected)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            MeasurementWorkflowSnapshot snapshot = orchestrator.snapshot();
            if (snapshot.state() == expected) return snapshot;
            Thread.sleep(10);
        }
        throw new AssertionError("Workflow did not reach state " + expected);
    }

    private static final class FakeGateway implements MeasurementWorkflowDeviceGateway {
        private final List<String> calls = Collections.synchronizedList(new ArrayList<>());
        private final CountDownLatch barcodeEntered = new CountDownLatch(1);
        private final CountDownLatch barcodeRelease = new CountDownLatch(1);
        private WorkflowPreflightReport preflight = WorkflowPreflightReport.ready();
        private int barcodeFailuresRemaining;
        private boolean blockBarcode;
        private volatile boolean cancelCalled;

        @Override
        public WorkflowPreflightReport preflight(List<MeasurementObjectRequest> objects,
                                                  MeasurementParameters parameters) {
            calls.add("preflight");
            return preflight;
        }

        @Override public void pickObject(MeasurementObjectRequest object) { add("pick", object); }
        @Override public void moveToBarcode(MeasurementObjectRequest object) { add("barcode-position", object); }

        @Override
        public String readBarcode(MeasurementObjectRequest object) {
            add("barcode", object);
            barcodeEntered.countDown();
            if (blockBarcode) {
                try {
                    barcodeRelease.await();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("barcode interrupted", exception);
                }
            }
            if (barcodeFailuresRemaining-- > 0) {
                throw new IllegalStateException("temporary barcode failure");
            }
            return "CODE-" + object.number();
        }

        @Override public void moveToScales(MeasurementObjectRequest object) { add("scales-position", object); }
        @Override public BigDecimal readWeight(MeasurementObjectRequest object) {
            add("weight", object);
            return new BigDecimal("125.5");
        }
        @Override public void moveToSpectrometer(MeasurementObjectRequest object) { add("spectrometer-position", object); }
        @Override public String acquireSpectrum(MeasurementObjectRequest object, MeasurementParameters parameters) {
            add("spectrum", object);
            return "spectrum-" + object.number();
        }
        @Override public void removeFromSpectrometer(MeasurementObjectRequest object) { add("spectrometer-remove", object); }
        @Override public void returnObject(MeasurementObjectRequest object) { add("return", object); }

        @Override
        public void cancelActiveOperation() {
            cancelCalled = true;
            barcodeRelease.countDown();
        }

        private void add(String operation, MeasurementObjectRequest object) {
            calls.add(operation + "-" + object.number());
        }
    }
}
