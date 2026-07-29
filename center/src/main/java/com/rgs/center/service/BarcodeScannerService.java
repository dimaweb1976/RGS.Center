package com.rgs.center.service;

import org.springframework.stereotype.Service;

/**
 * Фасад прикладного сценария чтения штрих-кода.
 */
@Service
public class BarcodeScannerService {

    /** Адаптер полного цикла getData. */
    private final BarcodeScannerGetDataAdapter getDataAdapter;

    /**
     * @param getDataAdapter адаптер чтения сканера
     */
    public BarcodeScannerService(BarcodeScannerGetDataAdapter getDataAdapter) {
        this.getDataAdapter = getDataAdapter;
    }

    /**
     * @return один штрих-код из очереди постоянного COM-подключения
     */
    public String readBarcode() {
        return getDataAdapter.getData();
    }
}
