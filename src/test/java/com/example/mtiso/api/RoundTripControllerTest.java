package com.example.mtiso.api;

import com.example.mtiso.mapper.Pacs008Mapper;
import com.example.mtiso.mapper.Pacs008ToMt103;
import com.example.mtiso.mapper.RoundTripService;
import com.example.mtiso.parser.MT103Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RoundTripControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MT103Parser parser = new MT103Parser();
        Pacs008Mapper mapper = new Pacs008Mapper();
        Pacs008ToMt103 reverseMapper = new Pacs008ToMt103(parser);
        RoundTripService roundTripService = new RoundTripService(parser, mapper, reverseMapper);
        RoundTripController controller = new RoundTripController(roundTripService);

        this.mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    private String loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed reading resource: " + path, e);
        }
    }

    @Test
    @DisplayName("POST /roundtrip returns 200 and lossless status for sample 01")
    void testRoundTripSuccess() throws Exception {
        String rawMt103 = loadResource("/samples/mt103/01_minimal_valid.txt");

        mockMvc.perform(post("/roundtrip")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(rawMt103))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.lossless").value(true))
                .andExpect(jsonPath("$.equalCount").value(6))
                .andExpect(jsonPath("$.lostCount").value(0))
                .andExpect(jsonPath("$.unsupportedCount").value(0))
                .andExpect(jsonPath("$.comparisons", hasSize(6)))
                .andExpect(jsonPath("$.pacs008Xml").isNotEmpty())
                .andExpect(jsonPath("$.reconstructedMt103").isNotEmpty())
                .andExpect(jsonPath("$.markdownSummary").value(containsString("**LOSSLESS**")));
    }

    @Test
    @DisplayName("POST /roundtrip returns 200 with LOST and UNSUPPORTED diffs for intentional data loss sample")
    void testRoundTripWithDataLoss() throws Exception {
        String rawMt103 = loadResource("/samples/mt103/11_intentional_data_loss.txt");

        mockMvc.perform(post("/roundtrip")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(rawMt103))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.lossless").value(false))
                .andExpect(jsonPath("$.equalCount").value(5))
                .andExpect(jsonPath("$.lostCount").value(1))
                .andExpect(jsonPath("$.unsupportedCount").value(2))
                .andExpect(jsonPath("$.markdownSummary").value(containsString("DATA LOSS / DIFFERENCES DETECTED")));
    }

    @Test
    @DisplayName("POST /roundtrip returns 400 for malformed MT103 parse error")
    void testRoundTripParseFailure() throws Exception {
        String malformedMt103 = "INVALID NOT A SWIFT MESSAGE";

        mockMvc.perform(post("/roundtrip")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(malformedMt103))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("PARSE_ERROR"))
                .andExpect(jsonPath("$.detail").value(containsString("Block 4")));
    }
}
