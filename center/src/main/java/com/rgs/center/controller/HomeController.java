package com.rgs.center.controller;

import com.rgs.center.objects.Device;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.CameraService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.service.RoboPlcService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.List;

@Controller
public class HomeController {

    private final CameraService cameraService;
    private final BosonService bosonService;
    private final NicoleService nicoleService;
    private final RoboPlcService roboPlcService;

    public HomeController(
            CameraService cameraService,
            BosonService bosonService,
            NicoleService nicoleService,
            RoboPlcService roboPlcService
    ) {
        this.cameraService = cameraService;
        this.bosonService = bosonService;
        this.nicoleService = nicoleService;
        this.roboPlcService = roboPlcService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/devices")
    public String devices(Model model) {
        model.addAttribute("devices", getVisibleDevices());
        return "devices";
    }

    @GetMapping("/devices/status")
    public String deviceStatus(Model model) {
        model.addAttribute("devices", getVisibleDevices());
        return "fragments/device-status :: status";
    }

    private List<Device> getVisibleDevices() {
        List<Device> devices = new ArrayList<>(cameraService.getCameras());
        devices.add(roboPlcService.getRoboPlc());
        devices.add(nicoleService.getNicole());
        devices.add(bosonService.getBoson());
        return devices;
    }
}
