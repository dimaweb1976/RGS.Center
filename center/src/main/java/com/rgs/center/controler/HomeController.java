package com.rgs.center.controller;

import com.rgs.center.objects.Device;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    @GetMapping("/")
    public String index(Model model) {

        model.addAttribute("title", "RGSCenter");
        model.addAttribute("status", "Laboratory Online");

        return "index";
    }

    @GetMapping("/devices")
    public String devices(Model model) {

        List<Device> devices = List.of(
                new Device("Camera", "ONLINE"),
                new Device("Barcode", "ONLINE"),
                new Device("Manipulator", "OFFLINE"),
                new Device("Scale", "ONLINE")
        );

        model.addAttribute("devices", devices);

        return "devices";
    }
}