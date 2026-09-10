package com.rgs.center.dashboard;

import com.rgs.center.dashboard.model.DashboardSnapshot;
import com.rgs.center.dashboard.model.EquipmentSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardSnapshotServiceTests {

    @Test
    void usesOneTimestampAndWrapsAssembledEquipment() {
        Instant now = Instant.parse("2026-09-10T14:00:00Z");
        EquipmentSnapshot equipment = mock(EquipmentSnapshot.class);
        EquipmentSnapshotAssembler assembler = mock(
                EquipmentSnapshotAssembler.class
        );
        when(assembler.assemble(now)).thenReturn(equipment);
        DashboardSnapshotService service = new DashboardSnapshotService(
                assembler,
                Clock.fixed(now, ZoneOffset.UTC)
        );

        DashboardSnapshot result = service.snapshot();

        assertEquals(DashboardSnapshot.CURRENT_SCHEMA_VERSION,
                result.schemaVersion());
        assertSame(equipment, result.equipment());
        verify(assembler).assemble(now);
    }
}
