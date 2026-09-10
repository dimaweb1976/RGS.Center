package com.rgs.center.dashboard;

import com.rgs.center.barcodescanner.BarcodeScannerOperationState;
import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.barcodescanner.BarcodeScannerSnapshot;
import com.rgs.center.camera.CameraImagingService;
import com.rgs.center.connection.DeviceEnablementService;
import com.rgs.center.dashboard.model.ConnectionState;
import com.rgs.center.dashboard.model.TechnicalState;
import com.rgs.center.gammapro.BosonConnectionStatus;
import com.rgs.center.gammapro.BosonHighVoltageSnapshot;
import com.rgs.center.gammapro.GammaProConnectionStatus;
import com.rgs.center.gammapro.GammaProHighVoltageValueType;
import com.rgs.center.gammapro.GammaProMeasurementState;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.gammapro.GammaProSnapshot;
import com.rgs.center.gammapro.GammaProStatusView;
import com.rgs.center.manipulator.ManipulatorConnectionStatus;
import com.rgs.center.manipulator.ManipulatorReadinessStatus;
import com.rgs.center.manipulator.ManipulatorStatusEvaluator;
import com.rgs.center.manipulator.ManipulatorStatusView;
import com.rgs.center.manipulator.RoboticManipulatorSnapshot;
import com.rgs.center.manipulator.RoboticManipulatorStreamService;
import com.rgs.center.manipulator.SpectrometerHatchStatus;
import com.rgs.center.objects.Device;
import com.rgs.center.objects.DeviceCode;
import com.rgs.center.objects.DeviceStatus;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.scales.ScalesSnapshot;
import com.rgs.center.service.CameraService;
import com.rgs.center.service.LenovoPcService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.service.SettingsService;
import com.rgs.center.shp.ShpReadinessStatus;
import com.rgs.center.shp.ShpService;
import com.rgs.center.shp.ShpSnapshot;
import com.rgs.center.shp.ShpStatusEvaluator;
import com.rgs.center.shp.ShpStatusView;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EquipmentSnapshotAssemblerTests {

    @Test
    void bosonConnectionComesFromGammaProProtocolStatus() {
        CameraService camera = mock(CameraService.class);
        CameraImagingService imaging = mock(CameraImagingService.class);
        LenovoPcService lenovo = mock(LenovoPcService.class);
        NicoleService nicole = mock(NicoleService.class);
        ShpService shp = mock(ShpService.class);
        ShpStatusEvaluator shpEvaluator = mock(ShpStatusEvaluator.class);
        ScalesService scales = mock(ScalesService.class);
        GammaProService gamma = mock(GammaProService.class);
        BarcodeScannerService barcode = mock(BarcodeScannerService.class);
        RoboticManipulatorStreamService manipulator = mock(
                RoboticManipulatorStreamService.class
        );
        ManipulatorStatusEvaluator manipulatorEvaluator = mock(
                ManipulatorStatusEvaluator.class
        );
        DeviceEnablementService enablement = mock(
                DeviceEnablementService.class
        );
        SettingsService settings = mock(SettingsService.class);
        Instant now = Instant.parse("2026-09-10T14:00:00Z");

        when(camera.getCameras()).thenReturn(List.of());
        Device lenovoDevice = new Device("LenovoPC", "192.168.201.20");
        lenovoDevice.setStatus(DeviceStatus.DISABLED);
        when(lenovo.getLenovoPc()).thenReturn(lenovoDevice);
        when(nicole.statusView()).thenReturn(
                com.rgs.center.nicole.NicoleStatusView.of(
                        null,
                        com.rgs.center.nicole.NicoleConnectionStatus.DISABLED,
                        com.rgs.center.nicole.NicoleReadinessStatus.DISABLED
                )
        );
        when(shp.snapshot()).thenReturn(new ShpSnapshot(
                "shp", "COM5", false, 9600, 0, 0, "", null, null
        ));
        when(shpEvaluator.evaluate(false, null)).thenReturn(
                ShpStatusView.noData()
        );
        when(scales.snapshot()).thenReturn(new ScalesSnapshot(
                "scales", "COM6", false, 9600, 0, 0, "", null, null
        ));

        GammaProSnapshot gammaSnapshot = new GammaProSnapshot(
                true,
                "127.0.0.1",
                37569,
                GammaProMeasurementState.IDLE,
                "MCA",
                "BOSON",
                60,
                List.of("TRACT1 (MCA, BOSON)"),
                "GET_TRACTS_NAME",
                "TRACT1 (MCA, BOSON)",
                ""
        );
        when(gamma.statusView()).thenReturn(new GammaProStatusView(
                GammaProConnectionStatus.CONNECTED,
                BosonConnectionStatus.CONNECTED,
                gammaSnapshot,
                now.minusSeconds(1),
                now.minusSeconds(1)
        ));
        when(gamma.bosonHighVoltageSnapshot()).thenReturn(
                new BosonHighVoltageSnapshot(
                        "MCA",
                        "BOSON",
                        true,
                        1000,
                        GammaProHighVoltageValueType.HIGH_VOLTAGE,
                        true,
                        now.minusSeconds(1).getEpochSecond(),
                        ""
                )
        );
        when(barcode.snapshot()).thenReturn(new BarcodeScannerSnapshot(
                "barcode-scanner", "COM7", false, false, false, 9600,
                BarcodeScannerOperationState.IDLE, 0, 3, 0, 0,
                "", "", "", null
        ));
        RoboticManipulatorSnapshot manipulatorSnapshot =
                new RoboticManipulatorSnapshot(
                        false, null, null, null, null, 0, null, ""
                );
        when(manipulator.snapshot()).thenReturn(manipulatorSnapshot);
        when(manipulatorEvaluator.evaluate(false, manipulatorSnapshot))
                .thenReturn(new ManipulatorStatusView(
                        ManipulatorConnectionStatus.DISABLED,
                        ManipulatorReadinessStatus.DISABLED,
                        SpectrometerHatchStatus.UNKNOWN,
                        List.of("Device is disabled by configuration")
                ));
        when(enablement.isEnabled(DeviceCode.BOSON)).thenReturn(true);
        when(settings.getValue(SettingsService.MANIPULATOR_HOST, String.class))
                .thenReturn("192.168.5.1");
        when(settings.getValue(
                SettingsService.MANIPULATOR_COMMAND_PORT, Long.class
        )).thenReturn(29999L);
        when(settings.getValue(
                SettingsService.MANIPULATOR_FEEDBACK_PORT, Long.class
        )).thenReturn(30004L);
        when(settings.getValue(
                SettingsService.MANIPULATOR_HATCH_CLOSED_DI_BIT, Long.class
        )).thenReturn(0L);
        when(settings.getValue(
                SettingsService.MANIPULATOR_HATCH_OPEN_DI_BIT, Long.class
        )).thenReturn(1L);

        EquipmentSnapshotAssembler assembler = new EquipmentSnapshotAssembler(
                camera,
                imaging,
                lenovo,
                nicole,
                shp,
                shpEvaluator,
                scales,
                gamma,
                barcode,
                manipulator,
                manipulatorEvaluator,
                enablement,
                settings,
                Duration.ofSeconds(30),
                "UTC"
        );

        var result = assembler.assemble(now);

        assertEquals(ConnectionState.ONLINE,
                result.boson().health().connection());
        assertEquals(TechnicalState.READY,
                result.boson().health().technical());
        assertEquals("CONNECTED", result.boson().sourceStatus());
        assertEquals(1000, result.boson().requestedHighVoltage());
    }
}
