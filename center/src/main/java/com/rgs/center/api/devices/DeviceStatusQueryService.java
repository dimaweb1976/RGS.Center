package com.rgs.center.api.devices;

import com.rgs.center.nicole.NicoleSnapshot;
import com.rgs.center.nicole.NicoleStatusView;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.CameraService;
import com.rgs.center.service.DeviceViewService;
import com.rgs.center.service.LenovoPcService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.shp.ShpService;
import com.rgs.center.shp.ShpStatusEvaluator;
import com.rgs.center.shp.ShpStatusView;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds a read-only API snapshot from existing device services.
 *
 * <p>This service never polls hardware and never mutates the current-data
 * showcase. It only converts already published state into stable REST DTOs.</p>
 */
@Service
public class DeviceStatusQueryService {

    private final CameraService cameraService;
    private final BosonService bosonService;
    private final NicoleService nicoleService;
    private final LenovoPcService lenovoPcService;
    private final DeviceViewService deviceViewService;
    private final ShpService shpService;
    private final ShpStatusEvaluator shpStatusEvaluator;

    public DeviceStatusQueryService(
            CameraService cameraService,
            BosonService bosonService,
            NicoleService nicoleService,
            LenovoPcService lenovoPcService,
            DeviceViewService deviceViewService,
            ShpService shpService,
            ShpStatusEvaluator shpStatusEvaluator
    ) {
        this.cameraService = cameraService;
        this.bosonService = bosonService;
        this.nicoleService = nicoleService;
        this.lenovoPcService = lenovoPcService;
        this.deviceViewService = deviceViewService;
        this.shpService = shpService;
        this.shpStatusEvaluator = shpStatusEvaluator;
    }

    /** @return one internally consistent, immutable view of current device data */
    public DevicesResponse snapshot() {
        NicoleStatusView nicole = nicoleService.statusView();
        var shpSnapshot = shpService.snapshot();
        ShpStatusView shp = shpStatusEvaluator.evaluate(
                shpSnapshot.connected(),
                shpSnapshot.measurements()
        );
        return new DevicesResponse(
                Instant.now(),
                deviceRows(),
                toResponse(nicole),
                toResponse(shp)
        );
    }

    private List<DeviceResponse> deviceRows() {
        List<DeviceResponse> rows = new ArrayList<>();
        List<Device> cameras = cameraService.getCameras();
        for (int index = 0; index < cameras.size(); index++) {
            String code = index == 0
                    ? DeviceCode.CAMERA.getCode()
                    : DeviceCode.CAMERA.getCode() + "-" + (index + 1);
            rows.add(toResponse(code, cameras.get(index)));
        }
        rows.add(toResponse(DeviceCode.LENOVO_PC, lenovoPcService.getLenovoPc()));
        rows.add(toResponse(DeviceCode.NICOLE, nicoleService.getNicole()));
        rows.add(toResponse(DeviceCode.BOSON, bosonService.getBoson()));
        rows.add(toResponse(DeviceCode.ROBOTIC_MANIPULATOR));
        rows.add(toResponse(DeviceCode.BARCODE_SCANNER));
        rows.add(toResponse(DeviceCode.SHP));
        rows.add(toResponse(DeviceCode.GAMMA_PRO));
        return rows;
    }

    private DeviceResponse toResponse(DeviceCode code, Device device) {
        return toResponse(code.getCode(), device);
    }

    private DeviceResponse toResponse(DeviceCode code) {
        return toResponse(code, deviceViewService.getDevice(code));
    }

    private DeviceResponse toResponse(String code, Device device) {
        return new DeviceResponse(
                code,
                device.getName(),
                device.getIpAddress(),
                device.getStatus() != com.rgs.center.objects.DeviceStatus.DISABLED,
                device.getStatus(),
                device.getLastCheck() == null
                        ? null
                        : device.getLastCheck()
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
        );
    }

    private NicoleStatusResponse toResponse(NicoleStatusView view) {
        return new NicoleStatusResponse(
                view.connectionStatus(),
                view.readiness(),
                view.warningText(),
                toResponse(view.snapshot())
        );
    }

    private NicoleSnapshotResponse toResponse(NicoleSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return new NicoleSnapshotResponse(
                snapshot.resetCause(),
                snapshot.ethernetUp(),
                snapshot.dhcpLeased(),
                snapshot.cryoControllerCommunication(),
                snapshot.cryocoolerStarted(),
                snapshot.advancedMode(),
                snapshot.currentNitrogenLevel(),
                snapshot.currentNitrogenPressure(),
                snapshot.lowerLevelVoltage(),
                snapshot.upperLevelVoltage(),
                snapshot.nitrogenLevelFrequency(),
                snapshot.controllerTime(),
                snapshot.coolerErrorCode(),
                snapshot.warningFlags(),
                snapshot.receivedAt()
        );
    }

    private ShpStatusResponse toResponse(ShpStatusView view) {
        return new ShpStatusResponse(
                view.ambientTemperatureCelsius(),
                view.humidityPercent(),
                view.atmosphericPressureHpa(),
                view.leakageCurrent(),
                view.chargeOutputVoltage(),
                view.readiness()
        );
    }
}
