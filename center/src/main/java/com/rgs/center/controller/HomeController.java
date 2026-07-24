package com.rgs.center.controller;

import com.rgs.center.service.CameraService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final CameraService cameraService;

    public HomeController(CameraService cameraService) {
        this.cameraService = cameraService;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/devices")
    public String devices(Model model) {
        model.addAttribute("camera", cameraService.getCamera());
        return "devices";
    }

    @GetMapping("/devices/status")
    public String deviceStatus(Model model) {
        model.addAttribute("camera", cameraService.getCamera());
        return "fragments/device-status :: status";
    }
}
