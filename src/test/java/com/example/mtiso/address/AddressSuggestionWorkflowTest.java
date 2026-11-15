package com.example.mtiso.address;

import com.example.mtiso.audit.ApprovalDto;
import com.example.mtiso.audit.AuditService;
import com.example.mtiso.audit.ConversionDetailResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AddressSuggestionWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditService auditService;

    @Test
    void testEndToEndAddressSuggestionApprovalAndReRunWorkflow() throws Exception {
        // 1. Load sample MT103 with unstructured Debtor (:50K:) address
        ClassPathResource sampleResource = new ClassPathResource("samples/mt103/06_50k_name_address.txt");
        String mt103Text = sampleResource.getContentAsString(StandardCharsets.UTF_8);

        // 2. Initial Conversion -> should produce REVIEW verdict due to unstructured address lines
        MvcResult convertResult = mockMvc.perform(post("/convert")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(mt103Text))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.report.verdict").value("REVIEW"))
                .andReturn();

        JsonNode convertResponse = objectMapper.readTree(convertResult.getResponse().getContentAsString());
        UUID conversionId = UUID.fromString(convertResponse.get("id").asText());
        assertThat(conversionId).isNotNull();

        // 3. Generate suggestions -> stored as PENDING
        MvcResult suggestionsResult = mockMvc.perform(post("/conversions/" + conversionId + "/suggestions")
                        .param("provider", "RULE_BASED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].partyType").value("DEBTOR"))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].provider").value("RULE_BASED"))
                .andExpect(jsonPath("$[0].street").value("100 MAIN STREET"))
                .andExpect(jsonPath("$[0].town").value("LONDON"))
                .andExpect(jsonPath("$[0].postCode").value("EC2N 2DB"))
                .andExpect(jsonPath("$[0].country").value("GB"))
                .andReturn();

        JsonNode suggestionsArray = objectMapper.readTree(suggestionsResult.getResponse().getContentAsString());
        UUID suggestionId = UUID.fromString(suggestionsArray.get(0).get("id").asText());

        // 4. Re-run BEFORE approval -> suggestion is PENDING so it must NOT be applied
        mockMvc.perform(post("/conversions/" + conversionId + "/rerun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.report.verdict").value("REVIEW"));

        // 5. Approve suggestion with actor and KYC audit notes
        String approveRequestBody = """
                {
                  "actor": "alice.compliance@bank.example",
                  "note": "Verified address against UK Companies House register"
                }
                """;

        mockMvc.perform(post("/suggestions/" + suggestionId + "/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(approveRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(suggestionId.toString()))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("alice.compliance@bank.example"))
                .andExpect(jsonPath("$.decisionNote").value("Verified address against UK Companies House register"));

        // 6. Verify decision was written to the central audit log (approvals table)
        ConversionDetailResponse auditDetail = auditService.getConversionDetail(conversionId).orElseThrow();
        assertThat(auditDetail.approvals()).anyMatch(approval ->
                "ADDRESS_SUGGESTION_APPROVED".equals(approval.action())
                        && "alice.compliance@bank.example".equals(approval.actor())
                        && approval.note().contains("Verified address against UK Companies House register")
        );

        // 7. Re-run conversion AFTER approval -> approved suggestion MUST be applied
        MvcResult rerunResult = mockMvc.perform(post("/conversions/" + conversionId + "/rerun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.report.verdict").value("SAFE"))
                .andReturn();

        JsonNode rerunResponse = objectMapper.readTree(rerunResult.getResponse().getContentAsString());
        String rerunXml = rerunResponse.get("pacs008Xml").asText();

        // Structured postal address elements are present in pacs.008 XML
        assertThat(rerunXml).contains("<StrtNm>100 MAIN STREET</StrtNm>");
        assertThat(rerunXml).contains("<TwnNm>LONDON</TwnNm>");
        assertThat(rerunXml).contains("<PstCd>EC2N 2DB</PstCd>");
        assertThat(rerunXml).contains("<Ctry>GB</Ctry>");

        // Unstructured AdrLine elements are NOT present for Debtor
        assertThat(rerunXml).doesNotContain("<AdrLine>100 MAIN STREET</AdrLine>");

        // Report summary is upgraded to SAFE
        assertThat(rerunResponse.get("report").get("verdict").asText()).isEqualTo("SAFE");
        assertThat(rerunResponse.get("report").get("summary").get("assumedCount").asInt()).isZero();
    }

    @Test
    void testRejectAddressSuggestionIsAuditedAndNeverApplied() throws Exception {
        ClassPathResource sampleResource = new ClassPathResource("samples/mt103/07_59_account_name.txt");
        String mt103Text = sampleResource.getContentAsString(StandardCharsets.UTF_8);

        // 1. Convert sample with unstructured Creditor (:59:) address
        MvcResult convertResult = mockMvc.perform(post("/convert")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(mt103Text))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode convertResponse = objectMapper.readTree(convertResult.getResponse().getContentAsString());
        UUID conversionId = UUID.fromString(convertResponse.get("id").asText());

        // 2. Generate suggestions
        MvcResult suggestionsResult = mockMvc.perform(post("/conversions/" + conversionId + "/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].partyType").value("CREDITOR"))
                .andReturn();

        JsonNode suggestionsArray = objectMapper.readTree(suggestionsResult.getResponse().getContentAsString());
        UUID suggestionId = UUID.fromString(suggestionsArray.get(0).get("id").asText());

        // 3. Reject the suggestion
        String rejectRequestBody = """
                {
                  "actor": "bob.analyst@bank.example",
                  "note": "Commercial registry mismatch on company address"
                }
                """;

        mockMvc.perform(post("/suggestions/" + suggestionId + "/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rejectRequestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decidedBy").value("bob.analyst@bank.example"));

        // 4. Verify rejection in audit trail
        ConversionDetailResponse auditDetail = auditService.getConversionDetail(conversionId).orElseThrow();
        assertThat(auditDetail.approvals()).anyMatch(approval ->
                "ADDRESS_SUGGESTION_REJECTED".equals(approval.action())
                        && "bob.analyst@bank.example".equals(approval.actor())
        );

        // 5. Re-run conversion -> rejected suggestion must NOT be applied (verdict remains REVIEW)
        mockMvc.perform(post("/conversions/" + conversionId + "/rerun"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.report.verdict").value("REVIEW"));
    }
}

