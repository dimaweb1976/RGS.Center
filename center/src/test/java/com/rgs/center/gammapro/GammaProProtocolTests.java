package com.rgs.center.gammapro;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GammaProProtocolTests {

    private static final Charset WINDOWS_1251 =
            Charset.forName("windows-1251");

    @Test
    void encodesCommandWithDocumentedLfCrEnding() {
        assertArrayEquals(
                new byte[]{'S', 'T', 'A', 'R', 'T', 0x0A, 0x0D},
                GammaProProtocol.encode("START", WINDOWS_1251)
        );
    }

    @Test
    void buildsStageSevenCommands() {
        assertEquals(
                "SET_TRACT(BOSON1,GCD-4020)",
                GammaProProtocol.setTract("BOSON1", "GCD-4020")
        );
        assertEquals("SET_PRESET(120)", GammaProProtocol.setPreset(120));
    }

    @Test
    void rejectsProtocolSeparatorsInParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> GammaProProtocol.setTract("BOSON1,STOP", "GCD-4020")
        );
    }

    @Test
    void parsesDataAndTerminalResponses() {
        GammaProProtocol.ResponseLine data = GammaProProtocol.parseResponse(
                "GET_TRACTS_NAME> DATA TRACT1(BOSON1,GCD-4020)"
        );
        GammaProProtocol.ResponseLine running = GammaProProtocol.parseResponse(
                "WAIT_MEAS_END=DO"
        );
        GammaProProtocol.ResponseLine completed = GammaProProtocol.parseResponse(
                "WAIT_MEAS_END=OK"
        );

        assertEquals(GammaProProtocol.ResponseType.DATA, data.type());
        assertEquals(
                "DATA TRACT1(BOSON1,GCD-4020)",
                data.payload()
        );
        assertEquals(GammaProProtocol.ResponseType.DO, running.type());
        assertEquals(GammaProProtocol.ResponseType.OK, completed.type());
    }

    @Test
    void parsesServerError() {
        GammaProProtocol.ResponseLine response =
                GammaProProtocol.parseResponse("SET_PRESET=ER");

        assertEquals(GammaProProtocol.ResponseType.ERROR, response.type());
        assertEquals("SET_PRESET", response.commandName());
    }
}
