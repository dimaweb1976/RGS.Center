package com.rgs.center.controller;

import com.rgs.center.barcodescanner.BarcodeScannerService;
import com.rgs.center.camera.CameraImagingOperations;
import com.rgs.center.dashboard.DashboardSnapshotService;
import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.gammapro.GammaProService;
import com.rgs.center.gdrm.GdrmService;
import com.rgs.center.measurement.MeasurementExecutionCoordinator;
import com.rgs.center.scales.ScalesService;
import com.rgs.center.service.BosonService;
import com.rgs.center.service.NicoleService;
import com.rgs.center.service.SettingsService;
import com.rgs.center.shp.ShpService;
import com.rgs.center.web.wizard.WizardHomeViewFactory;
import com.rgs.center.web.wizard.WizardServiceDeviceView;
import com.rgs.center.web.wizard.WizardServiceDeviceViewFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ui.ExtendedModelMap;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WizardServiceDevicesControllerTests {

    @Test
    void serviceReservationIsHeldUntilAsyncBarcodeScanCompletes() {
        DashboardSnapshotService dashboard = mock(DashboardSnapshotService.class);
        WizardServiceDeviceViewFactory views = mock(WizardServiceDeviceViewFactory.class);
        BarcodeScannerService barcode = mock(BarcodeScannerService.class);
        MeasurementExecutionCoordinator coordinator =
                new MeasurementExecutionCoordinator();
        DashboardSnapshot snapshot = mock(DashboardSnapshot.class);
        WizardServiceDeviceView device = new WizardServiceDeviceView(
                null,
                List.of(),
                List.of(),
                List.of(new WizardServiceDeviceView.Action(
                        "scan", "Start scan", "", true, false
                ))
        );
        when(dashboard.snapshot()).thenReturn(snapshot);
        when(views.detail(eq("barcode-scanner"), any())).thenReturn(device);
        ArgumentCaptor<Runnable> completion = ArgumentCaptor.forClass(Runnable.class);
        when(barcode.startRead(completion.capture())).thenReturn(true);
        WizardServiceDevicesController controller = controller(
                dashboard, views, coordinator, barcode
        );

        controller.action("barcode-scanner", "scan", new ExtendedModelMap());

        assertEquals(
                MeasurementExecutionCoordinator.Owner.SERVICE,
                coordinator.owner()
        );
        completion.getValue().run();
        assertEquals(
                MeasurementExecutionCoordinator.Owner.NONE,
                coordinator.owner()
        );
    }

    private static WizardServiceDevicesController controller(
            DashboardSnapshotService dashboard,
            WizardServiceDeviceViewFactory views,
            MeasurementExecutionCoordinator coordinator,
            BarcodeScannerService barcode
    ) {
        return new WizardServiceDevicesController(
                dashboard,
                mock(WizardHomeViewFactory.class),
                views,
                coordinator,
                mock(CameraImagingOperations.class),
                mock(NicoleService.class),
                mock(ShpService.class),
                mock(ScalesService.class),
                barcode,
                mock(GammaProService.class),
                mock(GdrmService.class),
                mock(BosonService.class),
                mock(SettingsService.class)
        );
    }
}
