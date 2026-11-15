package com.example.mtiso.api;

import com.example.mtiso.mapper.ConversionService;
import com.example.mtiso.mapper.Pacs008Mapper;
import com.example.mtiso.parser.MT103Parser;
import com.example.mtiso.report.ConversionReportGenerator;
import com.example.mtiso.validation.MT103Validator;
import com.example.mtiso.validation.Pacs008XmlValidator;
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

class ConversionControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MT103Parser parser = new MT103Parser();
        MT103Validator validator = new MT103Validator();
        Pacs008Mapper mapper = new Pacs008Mapper();
        Pacs008XmlValidator xmlValidator = new Pacs008XmlValidator();
        ConversionReportGenerator reportGenerator = new ConversionReportGenerator();
        ConversionService service = new ConversionService(parser, validator, mapper, xmlValidator, reportGenerator);

        ConversionController controller = new ConversionController(service);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
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
    @DisplayName("POST /convert: Success case returns HTTP 200 with status SUCCESS, pacs.008 XML, and SAFE report")
    void testConvertSuccess() throws Exception {
        String validMt103 = loadResource("/samples/mt103/01_minimal_valid.txt");

        mockMvc.perform(post("/convert")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(validMt103)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.pacs008Xml").value(containsString("<FIToFICstmrCdtTrf>")))
                .andExpect(jsonPath("$.pacs008Xml").value(containsString("<MsgId>SYNTH2026111501</MsgId>")))
                .andExpect(jsonPath("$.mappingNotes", not(empty())))
                .andExpect(jsonPath("$.findings[?(@.severity == 'ERROR')]", empty()))
                .andExpect(jsonPath("$.report.verdict").value("SAFE"))
                .andExpect(jsonPath("$.report.summary.mappedCount", greaterThan(0)))
                .andExpect(jsonPath("$.report.markdown").isNotEmpty());
    }

    @Test
    @DisplayName("POST /convert: Validation failure returns HTTP 200 with status VALIDATION_FAILED, findings, and BLOCK report")
    void testConvertValidationFailure() throws Exception {
        String missing32aMt103 = loadResource("/samples/mt103/08_missing_32a.txt");

        mockMvc.perform(post("/convert")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(missing32aMt103)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.pacs008Xml").isNotEmpty())
                .andExpect(jsonPath("$.findings", not(empty())))
                .andExpect(jsonPath("$.findings[?(@.code == 'TAG_MISSING' && @.tag == ':32A:')]", not(empty())))
                .andExpect(jsonPath("$.report.verdict").value("BLOCK"))
                .andExpect(jsonPath("$.report.verdictRule").value(containsString("BLOCK")));
    }

    @Test
    @DisplayName("POST /convert: Parse failure returns HTTP 400 with structured ParseErrorResponse")
    void testConvertParseFailure() throws Exception {
        String overlengthMt103 = loadResource("/samples/mt103/09_overlength_line.txt");

        mockMvc.perform(post("/convert")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(overlengthMt103)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("PARSE_ERROR"))
                .andExpect(jsonPath("$.error").value("Failed to parse MT103 message"))
                .andExpect(jsonPath("$.tag").value(":70:"))
                .andExpect(jsonPath("$.line").value(10))
                .andExpect(jsonPath("$.detail").value(containsString("exceeds SWIFT maximum of 35 characters")));
    }
}

