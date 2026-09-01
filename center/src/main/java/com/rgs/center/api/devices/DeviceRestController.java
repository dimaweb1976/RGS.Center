package com.rgs.center.api.devices;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only REST facade for the separate Next.js frontend. */
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceRestController {

    private final DeviceStatusQueryService queryService;

    public DeviceRestController(DeviceStatusQueryService queryService) {
        this.queryService = queryService;
    }

    /** @return current device, NICOLE and SHP state as JSON */
    @GetMapping
    public DevicesResponse devices() {
        return queryService.snapshot();
    }
}
