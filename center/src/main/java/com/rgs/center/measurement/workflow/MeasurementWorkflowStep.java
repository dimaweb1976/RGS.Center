package com.rgs.center.measurement.workflow;

/** Ordered operations performed for every detected object. */
public enum MeasurementWorkflowStep {
    PRECHECK("Preflight equipment check"),
    PICK_OBJECT("Pick object at source coordinates"),
    MOVE_TO_BARCODE("Move object to barcode scanner"),
    READ_BARCODE("Read and validate barcode"),
    MOVE_TO_SCALES("Move object to scales"),
    READ_WEIGHT("Read stable weight"),
    MOVE_TO_SPECTROMETER("Move object into spectrometer"),
    ACQUIRE_SPECTRUM("Acquire spectrum"),
    REMOVE_FROM_SPECTROMETER("Remove object from spectrometer"),
    RETURN_OBJECT("Return object to source coordinates");

    private final String label;

    MeasurementWorkflowStep(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
