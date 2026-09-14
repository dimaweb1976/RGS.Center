package com.rgs.center.web.wizard;

/** One operator-facing operation in the per-object production cycle. */
public record WizardWorkflowStepView(
        int number,
        String label,
        String status,
        String statusCssClass
) {
}
