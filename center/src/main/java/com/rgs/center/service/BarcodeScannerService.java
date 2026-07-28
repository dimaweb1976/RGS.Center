package com.rgs.center.service;

import org.springframework.stereotype.Service;

@Service
public class BarcodeScannerService {

    private final BarcodeScannerGetDataAdapter getDataAdapter;

    public BarcodeScannerService(BarcodeScannerGetDataAdapter getDataAdapter) {
        this.getDataAdapter = getDataAdapter;
    }

    public String readBarcode() {
        return getDataAdapter.getData();
    }
}
