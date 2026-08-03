package com.rgs.center.connection;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortTimeoutException;
import com.rgs.center.peripheral.PeripheralCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;

/**
 * COM-транспорт на базе jSerialComm.
 *
 * <p>Один экземпляр соответствует одному имени COM-порта и может обслуживаться
 * одним RX-потоком и одним TX-потоком. Скорость можно изменить между обменами,
 * что требуется для автоматического определения baud rate у GDRM.</p>
 */
public final class SerialDeviceConnection extends AbstractDeviceConnection {

    private static final Logger log =
            LoggerFactory.getLogger(SerialDeviceConnection.class);

    /** Низкоуровневый объект jSerialComm. */
    private final SerialPort port;

    /** Имя порта для диагностики, например COM7. */
    private final String portName;

    /** Текущая скорость обмена; изменяется только через {@link #setBaudRate(int)}. */
    private volatile int baudRate;

    /** Поток чтения создаётся после успешного открытия порта. */
    private InputStream inputStream;

    /**
     * @param portName системное имя COM-порта
     * @param baudRate скорость обмена
     * @param timeoutMillis тайм-аут блокирующего чтения
     */
    public SerialDeviceConnection(
            String portName,
            int baudRate,
            int timeoutMillis
    ) {
        super("serial:" + portName);
        this.portName = portName;
        this.baudRate = baudRate;
        port = SerialPort.getCommPort(portName);
        port.setComPortParameters(
                baudRate,
                8,
                SerialPort.ONE_STOP_BIT,
                SerialPort.NO_PARITY
        );
        port.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING,
                timeoutMillis,
                0
        );
    }

    /** Открывает COM-порт и создаёт поток чтения. */
    @Override
    protected void doOpen() {
        log.debug("Opening serial port {} at {} baud", portName, baudRate);
        port.setComPortParameters(
                baudRate,
                8,
                SerialPort.ONE_STOP_BIT,
                SerialPort.NO_PARITY
        );
        if (!port.openPort()) {
            log.error("Cannot open serial port {}", portName);
            throw new PeripheralCommunicationException(
                    "Cannot open serial port " + portName
            );
        }
        inputStream = port.getInputStream();
        log.info("Serial port {} opened at {} baud", portName, baudRate);
    }

    /** Очищает накопленные входящие и исходящие данные драйвера. */
    @Override
    public void clearInput() {
        requireOpen();
        if (!port.flushIOBuffers()) {
            log.error("Cannot clear serial port buffers {}", portName);
            throw new PeripheralCommunicationException(
                    "Cannot clear serial port buffers " + portName
            );
        }
        log.debug("Serial port {} buffers cleared", portName);
    }

    /**
     * Предоставляет поток чтения низкоуровневому reader.
     *
     * @return поток открытого COM-порта
     */
    public InputStream inputStream() {
        requireOpen();
        return inputStream;
    }

    /**
     * Блокирующе читает следующую порцию данных.
     *
     * @param buffer буфер назначения
     * @return число прочитанных байтов либо значение {@code <= 0} по тайм-ауту
     */
    @Override
    public int read(byte[] buffer) {
        requireOpen();
        try {
            int read = inputStream.read(buffer);
            if (read > 0) {
                log.trace("Read {} bytes from serial port {}", read, portName);
            }
            return read;
        } catch (SerialPortTimeoutException exception) {
            // Для semi-blocking режима отсутствие байтов до тайм-аута штатно:
            // RX-поток продолжит ожидание следующей порции данных.
            return 0;
        } catch (IOException exception) {
            log.error("Cannot read data from serial port {}", portName, exception);
            throw new PeripheralCommunicationException(
                    "Cannot read data from serial port " + portName,
                    exception
            );
        }
    }

    /**
     * Передаёт весь массив и проверяет фактическое число записанных байтов.
     *
     * @param value команда или данные для устройства
     */
    @Override
    public void write(byte[] value) {
        requireOpen();
        int written = port.writeBytes(value, value.length);
        if (written != value.length) {
            log.error(
                    "Incomplete write to serial port {}: expected={}, written={}",
                    portName,
                    value.length,
                    written
            );
            throw new PeripheralCommunicationException(
                    "Cannot write all data to serial port " + portName
            );
        }
        log.trace("Wrote {} bytes to serial port {}", written, portName);
    }

    /**
     * @return системное имя COM-порта
     */
    public String portName() {
        return portName;
    }

    /**
     * Меняет скорость COM-порта. Вызывающий код должен предварительно остановить
     * обслуживающий worker, чтобы в момент перенастройки не выполнялось чтение.
     *
     * @param value новая положительная скорость обмена
     */
    public synchronized void setBaudRate(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Baud rate must be positive");
        }
        baudRate = value;
        port.setComPortParameters(
                value,
                8,
                SerialPort.ONE_STOP_BIT,
                SerialPort.NO_PARITY
        );
        if (port.isOpen()) {
            port.flushIOBuffers();
        }
        log.info("Serial port {} configured for {} baud", portName, value);
    }

    /** @return текущая настроенная скорость COM-порта */
    public int baudRate() {
        return baudRate;
    }

    /** Закрывает поток и COM-порт. Повторный вызов безопасен. */
    @Override
    protected void doClose() {
        if (port.isOpen()) {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException exception) {
                // The port still has to be closed.
                log.warn(
                        "Cannot close input stream for serial port {}",
                        portName,
                        exception
                );
            }
            if (!port.closePort()) {
                log.warn("Serial port {} did not confirm closing", portName);
            } else {
                log.info("Serial port {} closed", portName);
            }
        }
        inputStream = null;
    }

}
