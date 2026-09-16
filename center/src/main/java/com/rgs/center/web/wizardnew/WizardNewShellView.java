package com.rgs.center.web.wizardnew;

import java.util.List;

/** Read-only view of the new shell; unavailable signals never appear as online. */
public record WizardNewShellView(
        String workflowState,
        String workflowDetail,
        List<StatusItem> statusItems
) {
    public WizardNewShellView {
        statusItems = List.copyOf(statusItems);
    }

    public record StatusItem(
            String label,
            String value,
            String detail,
            String tone
    ) {
    }
}
