package com.rgs.center.barcodescanner;

/**
 * Транспортный фасад чтения сканера через управляемое COM-подключение.
 */
public interface BarcodeScannerPort {

    /**
     * @param portName имя виртуального COM-порта
     * @param baudRate скорость обмена
     * @param timeoutMillis тайм-аут чтения
     * @param maxCodeLength ограничение длины кода
     * @return один прочитанный штрих-код
     */
    String readCode(
            String portName,
            int baudRate,
            int timeoutMillis,
            int maxCodeLength
    );
}
