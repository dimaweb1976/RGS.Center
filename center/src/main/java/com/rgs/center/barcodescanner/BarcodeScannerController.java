package com.rgs.center.barcodescanner;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** Thymeleaf endpoints for service operation of the barcode scanner. */
@Controller
@RequestMapping("/barcode-scanner")
public class BarcodeScannerController {

    private final BarcodeScannerService scannerService;

    public BarcodeScannerController(BarcodeScannerService scannerService) {
        this.scannerService = scannerService;
    }

    @GetMapping
    public String page(Model model) {
        addSnapshot(model);
        return "barcode-scanner";
    }

    @GetMapping("/status")
    public String status(Model model) {
        addSnapshot(model);
        return "fragments/barcode-scanner-status :: status";
    }

    @PostMapping("/read")
    public String read(Model model) {
        scannerService.startRead();
        return status(model);
    }

    @PostMapping("/cancel")
    public String cancel(Model model) {
        scannerService.cancelRead();
        return status(model);
    }

    private void addSnapshot(Model model) {
        boolean active = scannerService.isReadActive();
        model.addAttribute(
                "scanner",
                BarcodeScannerViewModel.from(
                        scannerService.snapshot(),
                        active
                )
        );
    }
}
