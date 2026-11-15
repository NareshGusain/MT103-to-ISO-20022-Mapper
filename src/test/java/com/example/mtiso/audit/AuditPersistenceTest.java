package com.example.mtiso.audit;

import com.example.mtiso.api.ConversionController;
import com.example.mtiso.api.ConversionResponse;
import com.example.mtiso.mapper.ConversionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AuditPersistenceTest {

    @Autowired
    private ConversionService conversionService;

    @Autowired
    private AuditService auditService;

    @Autowired
    private ConversionRepository conversionRepository;

    @Autowired
    private ApprovalRepository approvalRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.standaloneSetup(new ConversionController(conversionService, auditService)).build();
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
    @DisplayName("Audit Persistence: Saving conversion persists to DB with SHA-256 hash and report JSON")
    void testConversionPersistenceAndRetrieval() {
        String rawMt103 = loadResource("/samples/mt103/01_minimal_valid.txt");

        ConversionResponse response = conversionService.convert(rawMt103);
        UUID conversionId = response.id();

        assertThat(conversionId).isNotNull();

        // Query repository
        Optional<ConversionEntity> entityOpt = conversionRepository.findById(conversionId);
        assertThat(entityOpt).isPresent();

        ConversionEntity entity = entityOpt.get();
        assertThat(entity.getStatus()).isEqualTo("SUCCESS");
        assertThat(entity.getInputHash()).isEqualTo(AuditService.computeSha256(rawMt103));
        assertThat(entity.getInputText()).isEqualTo(rawMt103);
        assertThat(entity.getPacsXml()).contains("<FIToFICstmrCdtTrf>");
        assertThat(entity.getReportJson()).contains("\"verdict\":\"SAFE\"");
        assertThat(entity.getToolVersion()).isEqualTo(AuditService.TOOL_VERSION);
        assertThat(entity.getReceivedAt()).isNotNull();

        // Add an approval decision
        ApprovalDto approval = auditService.recordApproval(
                conversionId,
                "APPROVED",
                "chief_compliance_officer",
                "Reviewed and approved for STP execution"
        );

        assertThat(approval).isNotNull();
        assertThat(approval.action()).isEqualTo("APPROVED");
        assertThat(approval.actor()).isEqualTo("chief_compliance_officer");

        // Verify approval persisted in DB
        assertThat(approvalRepository.findByConversionId(conversionId)).hasSize(1);
    }

    @Test
    @DisplayName("GET /conversions/{id}: Returns 200 with complete audit trail and approvals")
    void testGetConversionEndpoint() throws Exception {
        String rawMt103 = loadResource("/samples/mt103/02_charge_our.txt");

        ConversionResponse response = conversionService.convert(rawMt103);
        UUID conversionId = response.id();

        // Attach an approval
        auditService.recordApproval(conversionId, "APPROVED", "operator_1", "Verified charges OUR");

        mockMvc.perform(get("/conversions/{id}", conversionId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(conversionId.toString()))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.inputHash").isNotEmpty())
                .andExpect(jsonPath("$.inputText").value(containsString(":20:SYNTH2026111502")))
                .andExpect(jsonPath("$.pacsXml").value(containsString("DEBT")))
                .andExpect(jsonPath("$.report.verdict").isNotEmpty())
                .andExpect(jsonPath("$.toolVersion").value(AuditService.TOOL_VERSION))
                .andExpect(jsonPath("$.approvals", hasSize(1)))
                .andExpect(jsonPath("$.approvals[0].action").value("APPROVED"))
                .andExpect(jsonPath("$.approvals[0].actor").value("operator_1"));
    }

    @Test
    @DisplayName("GET /conversions/{id}: Returns 404 for unknown UUID")
    void testGetConversionNotFound() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/conversions/{id}", unknownId)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
