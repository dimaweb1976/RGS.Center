package com.rgs.center.api.dashboard;

import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Дополнительный JSON-доступ к той же модели, которую использует Thymeleaf. */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardSnapshotController {

    private final DashboardSnapshotService snapshotService;

    public DashboardSnapshotController(
            DashboardSnapshotService snapshotService
    ) {
        this.snapshotService = snapshotService;
    }

    /** @return единый read-only снимок текущего оборудования */
    @GetMapping("/snapshot")
    public DashboardSnapshot snapshot() {
        return snapshotService.snapshot();
    }
}
