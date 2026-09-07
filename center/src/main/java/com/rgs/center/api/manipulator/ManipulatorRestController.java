package com.rgs.center.api.manipulator;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only REST API подробного состояния манипулятора. */
@RestController
@RequestMapping("/api/v1/manipulator")
public class ManipulatorRestController {

    private final ManipulatorStatusQueryService queryService;

    public ManipulatorRestController(
            ManipulatorStatusQueryService queryService
    ) {
        this.queryService = queryService;
    }

    /** @return последний подготовленный snapshot, без прямого TCP-опроса */
    @GetMapping("/status")
    public ManipulatorStatusResponse status() {
        return queryService.snapshot();
    }
}
