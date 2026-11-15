package com.example.mtiso.mapper;

import com.example.mtiso.iso.pacs008.*;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Pacs008MapperTest {

    private MT103Parser parser;
    private Pacs008Mapper mapper;
    private JAXBContext jaxbContext;

    @BeforeEach
    void setUp() throws Exception {
        parser = new MT103Parser();
        mapper = new Pacs008Mapper();
        jaxbContext = JAXBContext.newInstance(Document.class);
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

    private Document unmarshalXml(String xml) throws Exception {
        Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
        javax.xml.transform.stream.StreamSource source = new javax.xml.transform.stream.StreamSource(new StringReader(xml));
        jakarta.xml.bind.JAXBElement<Document> element = unmarshaller.unmarshal(source, Document.class);
        return element.getValue();
    }

    @Test
    @DisplayName("Golden Test 1: Map 01_minimal_valid.txt and verify against golden_01_minimal.xml")
    void testMapSample01AgainstGoldenFile() throws Exception {
        String mt103Raw = loadResource("/samples/mt103/01_minimal_valid.txt");
        String goldenXml = loadResource("/golden/golden_01_minimal.xml");

        MT103Message message = parser.parse(mt103Raw);
        MappingResult result = mapper.map(message);

        assertThat(result.xml()).isNotEmpty();
        assertThat(result.document()).isNotNull();

        Document goldenDoc = unmarshalXml(goldenXml);
        Document mappedDoc = result.document();

        GroupHeader131 mappedHdr = mappedDoc.getFIToFICstmrCdtTrf().getGrpHdr();
        GroupHeader131 goldenHdr = goldenDoc.getFIToFICstmrCdtTrf().getGrpHdr();
        assertThat(mappedHdr.getMsgId()).isEqualTo(goldenHdr.getMsgId());
        assertThat(mappedHdr.getNbOfTxs()).isEqualTo(goldenHdr.getNbOfTxs());
        assertThat(mappedHdr.getIntrBkSttlmDt()).isEqualTo(goldenHdr.getIntrBkSttlmDt());
        assertThat(mappedHdr.getSttlmInf().getSttlmMtd()).isEqualTo(goldenHdr.getSttlmInf().getSttlmMtd());

        CreditTransferTransaction73 mappedTx = mappedDoc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        CreditTransferTransaction73 goldenTx = goldenDoc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();

        assertThat(mappedTx.getPmtId().getInstrId()).isEqualTo(goldenTx.getPmtId().getInstrId());
        assertThat(mappedTx.getPmtId().getEndToEndId()).isEqualTo(goldenTx.getPmtId().getEndToEndId());
        assertThat(mappedTx.getPmtId().getTxId()).isEqualTo(goldenTx.getPmtId().getTxId());
        assertThat(mappedTx.getIntrBkSttlmAmt().getValue()).isEqualByComparingTo(goldenTx.getIntrBkSttlmAmt().getValue());
        assertThat(mappedTx.getIntrBkSttlmAmt().getCcy()).isEqualTo(goldenTx.getIntrBkSttlmAmt().getCcy());
        assertThat(mappedTx.getChrgBr()).isEqualTo(goldenTx.getChrgBr());

        assertThat(mappedTx.getDbtr().getNm()).isEqualTo(goldenTx.getDbtr().getNm());
        assertThat(mappedTx.getDbtrAcct().getId().getOthr().getId()).isEqualTo(goldenTx.getDbtrAcct().getId().getOthr().getId());
        assertThat(mappedTx.getCdtr().getNm()).isEqualTo(goldenTx.getCdtr().getNm());
        assertThat(mappedTx.getCdtrAcct().getId().getOthr().getId()).isEqualTo(goldenTx.getCdtrAcct().getId().getOthr().getId());

        assertThat(result.notes()).isNotEmpty();
        assertThat(result.notes()).anyMatch(n -> n.field().equals(":20:") && n.status() == MappingStatus.MAPPED);
        assertThat(result.notes()).anyMatch(n -> n.field().equals(":71A:") && n.status() == MappingStatus.MAPPED);
    }

    @Test
    @DisplayName("Golden Test 2: Map 02_charge_our.txt and verify against golden_02_charge_our.xml")
    void testMapSample02AgainstGoldenFile() throws Exception {
        String mt103Raw = loadResource("/samples/mt103/02_charge_our.txt");
        String goldenXml = loadResource("/golden/golden_02_charge_our.xml");

        MT103Message message = parser.parse(mt103Raw);
        MappingResult result = mapper.map(message);

        Document goldenDoc = unmarshalXml(goldenXml);
        CreditTransferTransaction73 mappedTx = result.document().getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        CreditTransferTransaction73 goldenTx = goldenDoc.getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();

        assertThat(mappedTx.getChrgBr()).isEqualTo(ChargeBearerType1Code.DEBT);
        assertThat(mappedTx.getChrgBr()).isEqualTo(goldenTx.getChrgBr());
        assertThat(mappedTx.getIntrBkSttlmAmt().getValue()).isEqualByComparingTo(new BigDecimal("50000.00"));
        assertThat(mappedTx.getRmtInf().getUstrd()).containsExactly("/INV/2026-OUR-01");
        assertThat(mappedTx.getRmtInf().getUstrd()).isEqualTo(goldenTx.getRmtInf().getUstrd());
    }

    @Test
    @DisplayName("Charge code BEN maps to ChargeBearer CRED")
    void testChargeCodeBenMapping() {
        String mt103Raw = loadResource("/samples/mt103/03_charge_ben.txt");
        MT103Message message = parser.parse(mt103Raw);
        MappingResult result = mapper.map(message);

        CreditTransferTransaction73 tx = result.document().getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        assertThat(tx.getChrgBr()).isEqualTo(ChargeBearerType1Code.CRED);
    }

    @Test
    @DisplayName("Multiline remittance :70: maps all lines to RmtInf/Ustrd")
    void testMultilineRemittanceMapping() {
        String mt103Raw = loadResource("/samples/mt103/05_multiline_remittance_70.txt");
        MT103Message message = parser.parse(mt103Raw);
        MappingResult result = mapper.map(message);

        CreditTransferTransaction73 tx = result.document().getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        assertThat(tx.getRmtInf()).isNotNull();
        assertThat(tx.getRmtInf().getUstrd()).containsExactly(
                "/INV/2026-9901",
                "PAYMENT FOR CONTRACT 88",
                "ANNUAL SOFTWARE SUBSCRIPTION",
                "TAX INVOICE ATTACHED"
        );
    }

    @Test
    @DisplayName("IBAN accounts in :50K: and :59: map to IBAN element and addresses to AdrLine")
    void testIbanAccountAndAddressMapping() {
        String mt103Raw50K = loadResource("/samples/mt103/06_50k_name_address.txt");
        MT103Message message50K = parser.parse(mt103Raw50K);
        MappingResult result50K = mapper.map(message50K);

        CreditTransferTransaction73 tx50K = result50K.document().getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        assertThat(tx50K.getDbtrAcct().getId().getIBAN()).isEqualTo("GB29NWBK60161331926819");
        assertThat(tx50K.getDbtr().getNm()).isEqualTo("ACME INDUSTRIAL SOLUTIONS");
        assertThat(tx50K.getDbtr().getPstlAdr().getAdrLine()).containsExactly(
                "100 MAIN STREET",
                "LONDON EC2N 2DB, UK"
        );

        String mt103Raw59 = loadResource("/samples/mt103/07_59_account_name.txt");
        MT103Message message59 = parser.parse(mt103Raw59);
        MappingResult result59 = mapper.map(message59);

        CreditTransferTransaction73 tx59 = result59.document().getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        assertThat(tx59.getCdtrAcct().getId().getIBAN()).isEqualTo("DE89370400440532013000");
        assertThat(tx59.getCdtr().getNm()).isEqualTo("BENEFICIARY IMPORT CORP");
        assertThat(tx59.getCdtr().getPstlAdr().getAdrLine()).containsExactly(
                "INDUSTRIESTRASSE 15",
                "60329 FRANKFURT, GERMANY"
        );
    }

    @Test
    @DisplayName("Unusual currency in :32A: (XOF) maps to settlement amount")
    void testUnusualCurrencyMapping() {
        String mt103Raw = loadResource("/samples/mt103/10_unusual_currency.txt");
        MT103Message message = parser.parse(mt103Raw);
        MappingResult result = mapper.map(message);

        CreditTransferTransaction73 tx = result.document().getFIToFICstmrCdtTrf().getCdtTrfTxInf().getFirst();
        assertThat(tx.getIntrBkSttlmAmt().getCcy()).isEqualTo("XOF");
        assertThat(tx.getIntrBkSttlmAmt().getValue()).isEqualByComparingTo(new BigDecimal("75000000"));
    }
}
