package com.rgs.center.web.wizard;

/** One row of the live automatic-run result table. */
public record WizardWorkflowObjectView(
        int number,
        String x,
        String y,
        String status,
        String statusCssClass,
        String barcode,
        String grossMass,
        String netMass,
        String spectrum,
        String error
) {
}
