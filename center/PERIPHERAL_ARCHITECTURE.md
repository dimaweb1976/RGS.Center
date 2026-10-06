# RGS.Center peripheral device architecture

## Equipment mapping

The target equipment scheme separates devices into two groups:

- network devices: Camera, Nicole, BOSON and RGS-plc;
- local RGS-nuvo COM devices: Barcode scanner, Scales and SHP.

Every physical device owns a separate `PeripheralDeviceWorker` and a
connection derived from `AbstractDeviceConnection`. LAN and COM devices use
the same bounded TX command queue, RX buffer and response-frame queue.

At Spring startup `DeviceConnectionManager` discovers every
`ManagedDeviceConnection`, attempts to open it and starts its RX/TX worker.
A failure of one device is logged but does not block the other devices or the
web application.

## Data flow

```text
application service
  |
  +--> normal/emergency command
          |
          v
      bounded command deque
          |
          v
      peripheral-<device>-tx
          |
          v
      AbstractDeviceConnection
          |
          +--> SerialDeviceConnection (COM)
          |
          +--> LanDeviceConnection (LAN)
          |
          v
      physical device / LAN reachability
          |
          v
      peripheral-<device>-rx
          |
          v
      bounded ByteRingBuffer
          |
          v
      PeripheralFrameDecoder
          |
          v
      bounded frame queue
          |
          v
      AbstractQueuedDeviceService
          |
          +--> AbstractNetworkDeviceService
          |      +--> CameraService
          |      +--> BosonService
          |      +--> NicoleService
          |      +--> RoboPlcService
          |
          +--> JSerialCommBarcodeScannerPort
```

## Components borrowed conceptually from GDRMonitor

- a connection object that owns the serial port;
- separate transmit and receive processing;
- a command queue with normal and emergency priority;
- a ring buffer between transport input and packet parsing;
- separation of transport frames from application processing;
- request/response exchange with a timeout;
- connection states and exchange statistics.

## Intentional Java 26 changes

The GDRMonitor source is used as an architectural reference, not copied
literally:

- `LinkedBlockingDeque` replaces a cross-thread unsynchronized `LinkedList`;
- `ReentrantLock` protects the ring buffer;
- queues and buffers are bounded and report overflow explicitly;
- blocking operations replace polling loops with `Thread.sleep`;
- `ExecutorService.shutdownNow()` and `awaitTermination()` replace
  `Thread.stop()`;
- frames and commands are copied at queue boundaries;
- every worker exposes a thread-safe immutable snapshot;
- framing is pluggable for line, fixed-length and future binary protocols.

## Extension contract

To add a device:

1. implement a transport derived from `AbstractDeviceConnection`, or reuse
   `LanDeviceConnection` / `SerialDeviceConnection`;
2. create a Spring service derived from `AbstractQueuedDeviceService`;
3. select a `PeripheralFrameDecoder` and queue limits;
4. implement protocol-specific command encoding and response decoding;
5. register settings and `DeviceCode`.

The new service is automatically discovered by `DeviceConnectionManager`;
the manager itself is not changed.

## Current integration

The barcode scanner is the first integrated peripheral:

1. configure its COM port;
2. open the connection and start dedicated RX/TX workers at application
   startup;
3. discard stale input at worker start;
4. assemble CR/LF-terminated barcode frames in the RX path;
5. return the next requested frame to `BarcodeScannerGetDataAdapter`;
6. keep the connection alive until application shutdown;
7. store data or error information in `CurrentDataService`.

Camera, BOSON, Nicole and RoboPLC inherit
`AbstractNetworkDeviceService`. Their scheduled `PING` command is enqueued in
the common TX queue; `ONLINE` / `OFFLINE` returns through the common RX queue
and is then recorded in `CurrentDataService`.

`DeviceCode` and `SettingsService` reserve entries for Scales and SHP.
Their services must be implemented after their serial parameters, commands,
frame formats and response validation rules are documented.
