package com.rgs.center.connection;

import com.rgs.center.peripheral.PeripheralWorkerState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "rgs.device.camera.enabled=false",
        "rgs.device.lenovo-pc.enabled=false",
        "rgs.device.nicole.enabled=false",
        "rgs.device.boson.enabled=false",
        "rgs.device.manipulator.enabled=false",
        "rgs.device.barcode-scanner.enabled=false",
        "rgs.device.gdrm.enabled=false",
        "rgs.device.shp.enabled=false",
        "rgs.device.gamma-pro.enabled=false"
})
class DisabledDevicesApplicationTests {

    @Autowired
    private List<ManagedDeviceConnection> devices;

    @Test
    void keepsEveryManagedTransportStopped() {
        assertFalse(devices.isEmpty());
        assertTrue(devices.stream().noneMatch(
                ManagedDeviceConnection::isEnabled
        ));
        assertTrue(devices.stream().allMatch(device ->
                device.communicationSnapshot().state()
                        == PeripheralWorkerState.STOPPED
        ));
    }
}
