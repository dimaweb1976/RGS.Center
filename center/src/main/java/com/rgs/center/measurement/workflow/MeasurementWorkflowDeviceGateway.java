package com.rgs.center.measurement.workflow;

import com.rgs.center.measurement.parameters.MeasurementParameters;

import java.math.BigDecimal;
import java.util.List;

/**
 * Boundary between the scenario state machine and device-specific protocols.
 * Every method returns only after the device has confirmed completion.
 */
public interface MeasurementWorkflowDeviceGateway {

    WorkflowPreflightReport preflight(
            List<MeasurementObjectRequest> objects,
            MeasurementParameters parameters
    );

    void pickObject(MeasurementObjectRequest object);

    void moveToBarcode(MeasurementObjectRequest object);

    String readBarcode(MeasurementObjectRequest object);

    void moveToScales(MeasurementObjectRequest object);

    BigDecimal readWeight(MeasurementObjectRequest object);

    void moveToSpectrometer(MeasurementObjectRequest object);

    String acquireSpectrum(
            MeasurementObjectRequest object,
            MeasurementParameters parameters
    );

    void removeFromSpectrometer(MeasurementObjectRequest object);

    void returnObject(MeasurementObjectRequest object);

    /** Best-effort cancellation of the operation currently owned by the run. */
    void cancelActiveOperation();
}
