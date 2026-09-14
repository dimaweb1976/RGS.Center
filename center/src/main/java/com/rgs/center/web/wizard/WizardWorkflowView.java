package com.rgs.center.web.wizard;

import java.util.List;

/** Presentation model of the production orchestrator monitor. */
public record WizardWorkflowView(
        String state,
        String stateCssClass,
        String headline,
        String details,
        int completedObjects,
        int totalObjects,
        int progressPercent,
        String activeObject,
        String activeStep,
        String estimatedCompletion,
        boolean canPause,
        boolean canResume,
        boolean canStop,
        boolean terminal,
        List<WizardWorkflowObjectView> objects,
        List<WizardWorkflowEventView> events
) {
    public WizardWorkflowView {
        objects = List.copyOf(objects);
        events = List.copyOf(events);
    }
}
