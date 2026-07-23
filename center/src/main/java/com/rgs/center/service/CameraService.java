package com.rgs.center.service;

import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.LocalDateTime;

@Service
public class CameraService {

    private static final String CAMERA_IP = "192.168.1.13";
    private static final int PING_TIMEOUT_MILLIS = 3000;

    private final Device camera = new Device("Camera", CAMERA_IP);

    @Scheduled(fixedDelay = 10000, initialDelay = 0)
    public void checkCamera() {
        boolean reachable = false;

        try {
            reachable = InetAddress
                    .getByName(CAMERA_IP)
                    .isReachable(PING_TIMEOUT_MILLIS);
        } catch (Exception ignored) {
            reachable = false;
        }

        camera.setStatus(
                reachable ? DeviceStatus.ONLINE : DeviceStatus.OFFLINE
        );
        camera.setLastCheck(LocalDateTime.now());
    }

    public Device getCamera() {
        return camera;
    }
}
