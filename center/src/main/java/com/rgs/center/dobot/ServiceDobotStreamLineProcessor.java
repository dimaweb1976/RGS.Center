package com.rgs.center.dobot;

import com.rgs.center.warehouse.RGSCenterWarehouse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ServiceDobotStreamLineProcessor {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    @Autowired
    private RGSCenterWarehouse warehouse;
    
    // Потокобезопасная очередь для поступающих пачек данных (1440 байт)
    private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
    private final Thread workerThread;
    private volatile boolean running = true;

    // Ожидаемое значение TestValue на смещении 0048 (0x0123456789ABCDEF в little-endian)
    private static final long EXPECTED_TEST_VALUE = 0x0123456789ABCDEF1L; // Будет проверяться побайтово или через ByteBuffer

    public ServiceDobotStreamLineProcessor() {
        this.workerThread = new Thread(this::processQueue, "dobot-processor-worker");
    }

    @PostConstruct
    public void start() {
        workerThread.start();
    }

    // Метод для приема готового пакета из соединения
    public void submit(byte[] packet) {
        queue.offer(packet);
    }

    private void processQueue() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                byte[] packet = queue.take();
                handlePacket(packet);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void handlePacket(byte[] packet) {
        if (packet == null || packet.length != 1440) {
            logger.error("Invalid packet length: " + (packet != null ? packet.length : 0));
            return;
        }

        // Все данные в реальном времени передаются в little-endian формате
        ByteBuffer buffer = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN);

        // 1. Проверяем MessageSize на смещении 0000-0001 (должно быть 1440 -> 0x05A0)
        int messageSize = buffer.getShort(00) & 0xFFFF;
        if (messageSize != 1440) {
            logger.error("Invalid MessageSize in packet: " + messageSize);
            return;
        }

        // 2. Проверяем TestValue на смещении 0048-0055 (0x0123456789ABCDEF)[cite: 1]
        long testValue = buffer.getLong(48);
        // В шестнадцатеричном виде стандартное значение: 0x0123456789ABCDEF
        long expectedTestVal = 0x0123456789ABCDEFL;
        if (testValue != expectedTestVal) {
            logger.error(String.format("TestValue mismatch! Expected: 0x%016X, Got: 0x%016X", expectedTestVal, testValue));
            return;
        }

        // 3. Парсим основные параметры из пакета
        long digitalInputs = buffer.getLong(8);   // DigitalInputs (0008-0015)[cite: 1]
        long digitalOutputs = buffer.getLong(16);  // DigitalOutputs (0016-0023)[cite: 1]
        long robotMode = buffer.getLong(24);       // RobotMode (0024-0031)[cite: 1]
        long timeStamp = buffer.getLong(32);       // TimeStamp (0032-0039)[cite: 1]
        double speedScaling = buffer.getDouble(64);// SpeedScaling (0064-0071)[cite: 1]

        // Актуальные углы суставов (QActual, 6 значений типа double, смещение 0432-0479)[cite: 1]
        double[] qActual = new double[6];
        for (int i = 0; i < 6; i++) {
            qActual[i] = buffer.getDouble(432 + (i * 8));
        }

        // Актуальные декартовы координаты TCP (ToolVectorActual, 6 значений типа double, смещение 0624-0671)[cite: 1]
        double[] toolVectorActual = new double[6];
        for (int i = 0; i < 6; i++) {
            toolVectorActual[i] = buffer.getDouble(624 + (i * 8));
        }

        // Извлекаем состояние ToolDI (DI0) для гриппера из пакета
        // (смещение 1008 — пример байта данных тул-интерфейса, при необходимости скорректируйте индекс)
        int toolDiByte = packet[1008] & 0xFF; 
        boolean gripperClosed = (toolDiByte & 0x01) != 0; // 0-й бит соответствует DI0

        // Сохраняем разобранные данные в склад/хранилище приложения
        warehouse.putItem( RGSCenterWarehouse.DEVICES_DOBOT_X, toolVectorActual[0]);
        warehouse.putItem( RGSCenterWarehouse.DEVICES_DOBOT_Y, toolVectorActual[1]);
        warehouse.putItem( RGSCenterWarehouse.DEVICES_DOBOT_Z, toolVectorActual[2]);
        
        warehouse.putItem( RGSCenterWarehouse.DEVICES_DOBOT_GRIPPER, gripperClosed);

        // Сохранение состояний блока DI / DO (битовые маски)
        warehouse.putItem( RGSCenterWarehouse.DEVICES_DOBOT_DIGITAL_INPUTS, digitalInputs);
        warehouse.putItem( RGSCenterWarehouse.DEVICES_DOBOT_DIGITAL_OUTPUTS, digitalOutputs);
        
        logger.debug(String.format("Successfully parsed Dobot packet. RobotMode: %d, X: %.2f", robotMode, toolVectorActual[0]));
    }

    @PreDestroy
    public void stop() {
        running = false;
        workerThread.interrupt();
    }
}