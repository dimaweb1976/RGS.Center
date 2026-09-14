package com.rgs.center.web.wizard;

import java.time.Instant;
import java.util.List;

/** Read-only модель первого экрана нового Thymeleaf-интерфейса. */
public record WizardHomeView(
        Instant generatedAt,
        List<WizardStatusItem> statusItems,
        List<WizardAlertView> alerts
) {
    public WizardHomeView {
        statusItems = List.copyOf(statusItems);
        alerts = List.copyOf(alerts);
    }
}
