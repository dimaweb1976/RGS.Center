package com.rgs.center.measurement.manual;

import com.rgs.center.measurement.parameters.MeasurementParameters;

import java.math.BigDecimal;

/** Testable boundary around existing queued physical-device services. */
public interface ManualMeasurementDeviceGateway {
    String scanBarcode();
    BigDecimal readWeight();
    void openHatch();
    void closeHatch();
    void enableHighVoltage();
    void disableHighVoltage();
    void verifySpectrumPreconditions();
    String acquireSpectrum(MeasurementParameters parameters);
    void stopSpectrumAcquisition();
}
