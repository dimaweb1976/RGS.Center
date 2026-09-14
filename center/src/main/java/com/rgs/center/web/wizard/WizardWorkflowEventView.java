package com.rgs.center.web.wizard;

/** Operator-facing representation of one workflow journal entry. */
public record WizardWorkflowEventView(
        String time,
        String context,
        String message
) {
}
