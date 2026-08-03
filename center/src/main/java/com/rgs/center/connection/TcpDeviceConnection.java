package com.rgs.center.connection;

import com.rgs.center.peripheral.PeripheralCommunicationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.Objects;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Реальное клиентское TCP-подключение для внешних программ и сетевых приборов.
 *
 * <p>Экземпляр владеет одним сокетом. Открытие, чтение, запись и закрытие
 * выполняются через общий жизненный цикл {@link AbstractDeviceConnection}, а
 * RX/TX-потоки создаются общим peripheral worker.</p>
 */
public final class TcpDeviceConnection extends AbstractDeviceConnection {

    private static final Logger log =
            LoggerFactory.getLogger(TcpDeviceConnection.class);

    private final Supplier<String> hostSupplier;
    private final IntSupplier portSupplier;
    private final IntSupplier connectTimeoutSupplier;
    private final IntSupplier readTimeoutSupplier;

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;

    /**
     * @param connectionId диагностическое имя подключения
     * @param hostSupplier источник IP-адреса или DNS-имени
     * @param portSupplier источник TCP-порта
     * @param connectTimeoutSupplier таймаут установления соединения, мс
     * @param readTimeoutSupplier таймаут одной операции чтения, мс
     */
    public TcpDeviceConnection(
            String connectionId,
            Supplier<String> hostSupplier,
            IntSupplier portSupplier,
            IntSupplier connectTimeoutSupplier,
            IntSupplier readTimeoutSupplier
    ) {
        super(connectionId);
        this.hostSupplier = Objects.requireNonNull(hostSupplier);
        this.portSupplier = Objects.requireNonNull(portSupplier);
        this.connectTimeoutSupplier = Objects.requireNonNull(
                connectTimeoutSupplier
        );
        this.readTimeoutSupplier = Objects.requireNonNull(readTimeoutSupplier);
    }

    /** Открывает TCP-сокет и подготавливает потоки ввода/вывода. */
    @Override
    protected void doOpen() {
        String host = hostSupplier.get();
        int port = portSupplier.getAsInt();
        if (host == null || host.isBlank()) {
            throw new PeripheralCommunicationException(
                    "TCP host is not configured for " + connectionId()
            );
        }
        if (port < 1 || port > 65535) {
            throw new PeripheralCommunicationException(
                    "Invalid TCP port " + port + " for " + connectionId()
            );
        }

        Socket openedSocket = new Socket();
        try {
            openedSocket.connect(
                    new InetSocketAddress(host, port),
                    positive(connectTimeoutSupplier.getAsInt())
            );
            openedSocket.setSoTimeout(
                    positive(readTimeoutSupplier.getAsInt())
            );
            openedSocket.setTcpNoDelay(true);
            socket = openedSocket;
            inputStream = openedSocket.getInputStream();
            outputStream = openedSocket.getOutputStream();
            log.info(
                    "TCP connection {} opened to {}:{}",
                    connectionId(),
                    host,
                    port
            );
        } catch (IOException exception) {
            closeQuietly(openedSocket);
            throw new PeripheralCommunicationException(
                    "Cannot connect " + connectionId()
                            + " to " + host + ":" + port,
                    exception
            );
        }
    }

    /**
     * Читает очередную порцию TCP-данных; обычный read timeout не считается
     * разрывом соединения и позволяет RX-потоку продолжать ожидание.
     */
    @Override
    public int read(byte[] buffer) {
        requireOpen();
        try {
            return inputStream.read(buffer);
        } catch (SocketTimeoutException exception) {
            return 0;
        } catch (IOException exception) {
            throw new PeripheralCommunicationException(
                    "Cannot read from " + connectionId(),
                    exception
            );
        }
    }

    /** Передаёт и немедленно сбрасывает в сокет весь кадр команды. */
    @Override
    public void write(byte[] value) {
        requireOpen();
        try {
            outputStream.write(value);
            outputStream.flush();
        } catch (IOException exception) {
            throw new PeripheralCommunicationException(
                    "Cannot write to " + connectionId(),
                    exception
            );
        }
    }

    /** Удаляет уже принятые, но ещё не обработанные байты сокета. */
    @Override
    public void clearInput() {
        requireOpen();
        try {
            byte[] discard = new byte[512];
            while (inputStream.available() > 0) {
                int read = inputStream.read(
                        discard,
                        0,
                        Math.min(discard.length, inputStream.available())
                );
                if (read <= 0) {
                    break;
                }
            }
        } catch (IOException exception) {
            throw new PeripheralCommunicationException(
                    "Cannot clear input of " + connectionId(),
                    exception
            );
        }
    }

    /** Закрывает сокет и освобождает связанные потоки. */
    @Override
    protected void doClose() {
        closeQuietly(socket);
        socket = null;
        inputStream = null;
        outputStream = null;
    }

    private static int positive(int value) {
        return Math.max(1, value);
    }

    private static void closeQuietly(Socket value) {
        if (value == null) {
            return;
        }
        try {
            value.close();
        } catch (IOException ignored) {
            // Основная ошибка открытия/обмена важнее ошибки освобождения сокета.
        }
    }
}
