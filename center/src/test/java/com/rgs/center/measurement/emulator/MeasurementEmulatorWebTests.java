package com.rgs.center.measurement.emulator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MeasurementEmulatorWebTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rendersCameraControlsRecognitionResultAndEventJournal() throws Exception {
        mockMvc.perform(get("/measurement-emulator"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Сделать снимок")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Распознать")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Результат распознавания")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Журнал событий")
                ))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString("'Оригинал: ' +")
                        )
                ));
    }
}
