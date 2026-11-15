package com.example.mtiso.mapper;

import com.example.mtiso.address.AddressSuggestion;
import com.example.mtiso.address.PartyType;
import com.example.mtiso.iso.pacs008.*;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.RawTag;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import org.springframework.stereotype.Component;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Deterministic, rule-based mapper converting SWIFT MT103 messages to ISO 20022 pacs.008.001.14 XML.
 * Each transformation rule references Swift CBPR+ usage guidelines or is tagged as ASSUMPTION - verify.
 */
@Component
public class Pacs008Mapper {

    private static final Pattern IBAN_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z0-9]{1,30}$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("uuMMdd");

    private final ObjectFactory objectFactory;
    private final DatatypeFactory datatypeFactory;
    private final JAXBContext jaxbContext;

    public Pacs008Mapper() {
        this.objectFactory = new ObjectFactory();
        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
            this.jaxbContext = JAXBContext.newInstance(Document.class);
        } catch (DatatypeConfigurationException | JAXBException e) {
            throw new IllegalStateException("Failed to initialize JAXB context or DatatypeFactory for pacs.008", e);
        }
    }

    /**
     * Maps an MT103Message into an ISO 20022 pacs.008 Document, serializes to XML,
     * and produces a list of MappingNotes recording field transformations.
     */
    public MappingResult map(MT103Message mt103) {
        return map(mt103, Collections.emptyMap());
    }

    /**
     * Maps an MT103Message into an ISO 20022 pacs.008 Document, serializes to XML,
     * applying any approved structured address suggestions for debtor and creditor,
     * and produces a list of MappingNotes recording field transformations.
     */
    public MappingResult map(MT103Message mt103, Map<PartyType, AddressSuggestion> approvedSuggestions) {
        Objects.requireNonNull(mt103, "MT103Message cannot be null");
        Map<PartyType, AddressSuggestion> suggestions = approvedSuggestions != null ? approvedSuggestions : Collections.emptyMap();

        List<MappingNote> notes = new ArrayList<>();
        Document doc = objectFactory.createDocument();
        FIToFICustomerCreditTransferV14 cdtTrf = objectFactory.createFIToFICustomerCreditTransferV14();
        doc.setFIToFICstmrCdtTrf(cdtTrf);

        // 1. Group Header
        GroupHeader131 grpHdr = objectFactory.createGroupHeader131();
        cdtTrf.setGrpHdr(grpHdr);

        // Guideline: Swift CBPR+ pacs.008 - Single customer credit transfer sets NbOfTxs to 1
        grpHdr.setNbOfTxs("1");

        // ASSUMPTION - verify: GrpHdr/CreDtTm generated from system UTC time at conversion execution
        ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
        XMLGregorianCalendar creDtTm = datatypeFactory.newXMLGregorianCalendar(
                GregorianCalendar.from(nowUtc)
        );
        grpHdr.setCreDtTm(creDtTm);

        // ASSUMPTION - verify: SettlementMethod1Code defaulted to CLRG for interbank clearing settlement
        SettlementInstruction15 sttlmInf = objectFactory.createSettlementInstruction15();
        sttlmInf.setSttlmMtd(SettlementMethod1Code.CLRG);
        grpHdr.setSttlmInf(sttlmInf);

        // 2. Credit Transfer Transaction Information
        CreditTransferTransaction73 txInf = objectFactory.createCreditTransferTransaction73();
        cdtTrf.getCdtTrfTxInf().add(txInf);

        // 3. Field :20: -> Message Identification & Instruction Identification
        mapTag20(mt103, grpHdr, txInf, notes);

        // 4. Field :32A: -> Settlement Date, Currency, and Amount
        mapTag32A(mt103, grpHdr, txInf, notes);

        // 5. Field :50K: -> Debtor Account, Name, and Address
        mapTag50K(mt103, txInf, notes, suggestions);

        // 6. Field :59: -> Creditor Account, Name, and Address
        mapTag59(mt103, txInf, notes, suggestions);

        // 7. Agents (DbtrAgt & CdtrAgt) from SWIFT Block 1 & 2 Headers
        mapFinancialInstitutions(mt103, grpHdr, txInf, notes);

        // 8. Field :70: -> Remittance Information (Unstructured)
        mapTag70(mt103, txInf, notes);

        // 9. Field :71A: -> Details of Charges (Charge Bearer)
        mapTag71A(mt103, txInf, notes);

        // 9. Serialize Document to formatted XML
        String xml = marshalDocument(doc);

        return new MappingResult(xml, doc, Collections.unmodifiableList(notes));
    }

    /**
     * Maps MT103 Tag :20: (Transaction Reference Number).
     * Guideline: Swift CBPR+ pacs.008 Base - Field :20: maps to GrpHdr/MsgId and PmtId/InstrId, EndToEndId, and TxId.
     */
    private void mapTag20(MT103Message mt103, GroupHeader131 grpHdr, CreditTransferTransaction73 txInf, List<MappingNote> notes) {
        Optional<RawTag> tag20Opt = mt103.tag20();
        PaymentIdentification13 pmtId = objectFactory.createPaymentIdentification13();
        txInf.setPmtId(pmtId);

        if (tag20Opt.isPresent()) {
            String ref = tag20Opt.get().value().trim();
            // Guideline: Swift CBPR+ pacs.008 Base - GrpHdr/MsgId mapped from MT103 :20:
            grpHdr.setMsgId(ref);

            // Guideline: Swift CBPR+ pacs.008 Base - PmtId/InstrId mapped from MT103 :20:
            pmtId.setInstrId(ref);

            // Guideline: Swift CBPR+ pacs.008 Base - PmtId/EndToEndId mapped from MT103 :20: when :21: absent
            pmtId.setEndToEndId(ref);

            // Guideline: Swift CBPR+ pacs.008 Base - PmtId/TxId mapped from MT103 :20:
            pmtId.setTxId(ref);

            notes.add(MappingNote.mapped(":20:", String.format("Mapped reference '%s' to MsgId, InstrId, EndToEndId, and TxId", ref)));
        } else {
            // ASSUMPTION - verify: When :20: is missing, EndToEndId is set to NOTPROVIDED to satisfy schema mandatory requirement
            pmtId.setEndToEndId("NOTPROVIDED");
            notes.add(MappingNote.unmapped(":20:", "Field :20: missing; EndToEndId defaulted to 'NOTPROVIDED' to satisfy schema"));
        }
    }

    /**
     * Maps MT103 Tag :32A: (Value Date, Currency, Amount).
     * Guideline: Swift CBPR+ pacs.008 - Date maps to IntrBkSttlmDt, currency to IntrBkSttlmAmt/@Ccy, amount to IntrBkSttlmAmt.
     */
    private void mapTag32A(MT103Message mt103, GroupHeader131 grpHdr, CreditTransferTransaction73 txInf, List<MappingNote> notes) {
        Optional<RawTag> tag32AOpt = mt103.tag32A();
        if (tag32AOpt.isEmpty()) {
            notes.add(MappingNote.unmapped(":32A:", "Field :32A: is missing; settlement date and amount omitted"));
            return;
        }

        String raw = tag32AOpt.get().value().trim();
        if (raw.length() < 10) {
            notes.add(MappingNote.unmapped(":32A:", "Field :32A: length too short for date, currency, and amount extraction"));
            return;
        }

        String dateStr = raw.substring(0, 6);
        String ccy = raw.substring(6, 9);
        String amtStr = raw.substring(9);

        // Guideline: Swift CBPR+ pacs.008 - Date YYMMDD converted to YYYY-MM-DD
        try {
            LocalDate localDate = LocalDate.parse(dateStr, DATE_FORMATTER);
            XMLGregorianCalendar sttlmDt = datatypeFactory.newXMLGregorianCalendarDate(
                    localDate.getYear(),
                    localDate.getMonthValue(),
                    localDate.getDayOfMonth(),
                    DatatypeConstants.FIELD_UNDEFINED
            );
            txInf.setIntrBkSttlmDt(sttlmDt);
            grpHdr.setIntrBkSttlmDt(sttlmDt);
            notes.add(MappingNote.mapped(":32A:[Date]", String.format("Mapped date '%s' to IntrBkSttlmDt '%s'", dateStr, localDate)));
        } catch (Exception e) {
            notes.add(MappingNote.unmapped(":32A:[Date]", "Failed to parse date: " + dateStr));
        }

        // Guideline: Swift CBPR+ pacs.008 - Amount with comma decimal converted to BigDecimal ActiveCurrencyAndAmount
        try {
            String sanitizedAmt = amtStr.endsWith(",") ? amtStr.substring(0, amtStr.length() - 1) : amtStr.replace(',', '.');
            BigDecimal amount = new BigDecimal(sanitizedAmt);

            ActiveCurrencyAndAmount sttlmAmt = objectFactory.createActiveCurrencyAndAmount();
            sttlmAmt.setCcy(ccy);
            sttlmAmt.setValue(amount);
            txInf.setIntrBkSttlmAmt(sttlmAmt);

            // Also set total in group header
            ActiveCurrencyAndAmount ttlAmt = objectFactory.createActiveCurrencyAndAmount();
            ttlAmt.setCcy(ccy);
            ttlAmt.setValue(amount);
            grpHdr.setTtlIntrBkSttlmAmt(ttlAmt);

            notes.add(MappingNote.mapped(":32A:[Amount&Ccy]", String.format("Mapped currency '%s' and amount '%s' to IntrBkSttlmAmt", ccy, amount)));
        } catch (Exception e) {
            notes.add(MappingNote.unmapped(":32A:[Amount&Ccy]", "Failed to parse amount/currency: " + raw));
        }
    }

    /**
     * Maps MT103 Tag :50K: (Ordering Customer).
     * Guideline: Swift CBPR+ pacs.008 - Account line to DbtrAcct/Id, name to Dbtr/Nm, remaining lines to Dbtr/PstlAdr/AdrLine.
     * When an approved address suggestion is provided, structured postal address elements are populated instead.
     */
    private void mapTag50K(
            MT103Message mt103,
            CreditTransferTransaction73 txInf,
            List<MappingNote> notes,
            Map<PartyType, AddressSuggestion> approvedSuggestions
    ) {
        Optional<RawTag> tag50KOpt = mt103.tag50K();
        if (tag50KOpt.isEmpty()) {
            notes.add(MappingNote.unmapped(":50K:", "Field :50K: is missing"));
            return;
        }

        List<String> lines = tag50KOpt.get().lines();
        if (lines.isEmpty()) {
            notes.add(MappingNote.unmapped(":50K:", "Field :50K: has no lines"));
            return;
        }

        PartyIdentification272 dbtr = objectFactory.createPartyIdentification272();
        txInf.setDbtr(dbtr);

        int lineIdx = 0;
        String firstLine = lines.get(0).trim();

        // Guideline: Swift CBPR+ pacs.008 - Line 1 starting with '/' represents account
        if (firstLine.startsWith("/")) {
            String acctNum = firstLine.substring(1).trim();
            CashAccount40 dbtrAcct = objectFactory.createCashAccount40();
            AccountIdentification4Choice acctId = objectFactory.createAccountIdentification4Choice();
            dbtrAcct.setId(acctId);

            if (IBAN_PATTERN.matcher(acctNum).matches()) {
                acctId.setIBAN(acctNum);
                notes.add(MappingNote.mapped(":50K:[Account]", "Mapped IBAN account to DbtrAcct/Id/IBAN: " + acctNum));
            } else {
                GenericAccountIdentification1 othr = objectFactory.createGenericAccountIdentification1();
                othr.setId(acctNum);
                acctId.setOthr(othr);
                notes.add(MappingNote.mapped(":50K:[Account]", "Mapped proprietary account to DbtrAcct/Id/Othr/Id: " + acctNum));
            }
            txInf.setDbtrAcct(dbtrAcct);
            lineIdx++;
        }

        // Guideline: Swift CBPR+ pacs.008 - First non-account line is Debtor Name (Dbtr/Nm)
        if (lineIdx < lines.size()) {
            String name = lines.get(lineIdx).trim();
            dbtr.setNm(name);
            notes.add(MappingNote.mapped(":50K:[Name]", "Mapped debtor name to Dbtr/Nm: " + name));
            lineIdx++;
        }

        // Check if an approved structured address suggestion is present
        if (approvedSuggestions.containsKey(PartyType.DEBTOR)) {
            AddressSuggestion suggestion = approvedSuggestions.get(PartyType.DEBTOR);
            PostalAddress27 pstlAdr = objectFactory.createPostalAddress27();
            dbtr.setPstlAdr(pstlAdr);

            if (suggestion.street().isPresent()) {
                pstlAdr.setStrtNm(suggestion.street().value());
            }
            if (suggestion.town().isPresent()) {
                pstlAdr.setTwnNm(suggestion.town().value());
            }
            if (suggestion.postCode().isPresent()) {
                pstlAdr.setPstCd(suggestion.postCode().value());
            }
            if (suggestion.country().isPresent()) {
                pstlAdr.setCtry(suggestion.country().value());
            }

            notes.add(MappingNote.mapped(
                    ":50K:[Address]",
                    String.format(
                            "Mapped structured debtor address from approved suggestion: street='%s', town='%s', postCode='%s', country='%s'",
                            suggestion.getStreetValue(),
                            suggestion.getTownValue(),
                            suggestion.getPostCodeValue(),
                            suggestion.getCountryValue()
                    )
            ));
        } else if (lineIdx < lines.size()) {
            // ASSUMPTION - verify: Remaining free-text lines mapped directly to PostalAddress27/AdrLine
            PostalAddress27 pstlAdr = objectFactory.createPostalAddress27();
            dbtr.setPstlAdr(pstlAdr);
            for (int i = lineIdx; i < lines.size(); i++) {
                String adrLine = lines.get(i).trim();
                if (!adrLine.isEmpty()) {
                    pstlAdr.getAdrLine().add(adrLine);
                }
            }
            notes.add(MappingNote.assumed(":50K:[Address]", "Mapped unstructured address lines to Dbtr/PstlAdr/AdrLine"));
        }
    }

    /**
     * Maps MT103 Tag :59: (Beneficiary Customer).
     * Guideline: Swift CBPR+ pacs.008 - Account line to CdtrAcct/Id, name to Cdtr/Nm, remaining lines to Cdtr/PstlAdr/AdrLine.
     * When an approved address suggestion is provided, structured postal address elements are populated instead.
     */
    private void mapTag59(
            MT103Message mt103,
            CreditTransferTransaction73 txInf,
            List<MappingNote> notes,
            Map<PartyType, AddressSuggestion> approvedSuggestions
    ) {
        Optional<RawTag> tag59Opt = mt103.tag59();
        if (tag59Opt.isEmpty()) {
            notes.add(MappingNote.unmapped(":59:", "Field :59: is missing"));
            return;
        }

        List<String> lines = tag59Opt.get().lines();
        if (lines.isEmpty()) {
            notes.add(MappingNote.unmapped(":59:", "Field :59: has no lines"));
            return;
        }

        PartyIdentification272 cdtr = objectFactory.createPartyIdentification272();
        txInf.setCdtr(cdtr);

        int lineIdx = 0;
        String firstLine = lines.get(0).trim();

        // Guideline: Swift CBPR+ pacs.008 - Line 1 starting with '/' represents account
        if (firstLine.startsWith("/")) {
            String acctNum = firstLine.substring(1).trim();
            CashAccount40 cdtrAcct = objectFactory.createCashAccount40();
            AccountIdentification4Choice acctId = objectFactory.createAccountIdentification4Choice();
            cdtrAcct.setId(acctId);

            if (IBAN_PATTERN.matcher(acctNum).matches()) {
                acctId.setIBAN(acctNum);
                notes.add(MappingNote.mapped(":59:[Account]", "Mapped IBAN account to CdtrAcct/Id/IBAN: " + acctNum));
            } else {
                GenericAccountIdentification1 othr = objectFactory.createGenericAccountIdentification1();
                othr.setId(acctNum);
                acctId.setOthr(othr);
                notes.add(MappingNote.mapped(":59:[Account]", "Mapped proprietary account to CdtrAcct/Id/Othr/Id: " + acctNum));
            }
            txInf.setCdtrAcct(cdtrAcct);
            lineIdx++;
        }

        // Guideline: Swift CBPR+ pacs.008 - First non-account line is Creditor Name (Cdtr/Nm)
        if (lineIdx < lines.size()) {
            String name = lines.get(lineIdx).trim();
            cdtr.setNm(name);
            notes.add(MappingNote.mapped(":59:[Name]", "Mapped creditor name to Cdtr/Nm: " + name));
            lineIdx++;
        }

        // Check if an approved structured address suggestion is present
        if (approvedSuggestions.containsKey(PartyType.CREDITOR)) {
            AddressSuggestion suggestion = approvedSuggestions.get(PartyType.CREDITOR);
            PostalAddress27 pstlAdr = objectFactory.createPostalAddress27();
            cdtr.setPstlAdr(pstlAdr);

            if (suggestion.street().isPresent()) {
                pstlAdr.setStrtNm(suggestion.street().value());
            }
            if (suggestion.town().isPresent()) {
                pstlAdr.setTwnNm(suggestion.town().value());
            }
            if (suggestion.postCode().isPresent()) {
                pstlAdr.setPstCd(suggestion.postCode().value());
            }
            if (suggestion.country().isPresent()) {
                pstlAdr.setCtry(suggestion.country().value());
            }

            notes.add(MappingNote.mapped(
                    ":59:[Address]",
                    String.format(
                            "Mapped structured creditor address from approved suggestion: street='%s', town='%s', postCode='%s', country='%s'",
                            suggestion.getStreetValue(),
                            suggestion.getTownValue(),
                            suggestion.getPostCodeValue(),
                            suggestion.getCountryValue()
                    )
            ));
        } else if (lineIdx < lines.size()) {
            // ASSUMPTION - verify: Remaining free-text lines mapped directly to PostalAddress27/AdrLine
            PostalAddress27 pstlAdr = objectFactory.createPostalAddress27();
            cdtr.setPstlAdr(pstlAdr);
            for (int i = lineIdx; i < lines.size(); i++) {
                String adrLine = lines.get(i).trim();
                if (!adrLine.isEmpty()) {
                    pstlAdr.getAdrLine().add(adrLine);
                }
            }
            notes.add(MappingNote.assumed(":59:[Address]", "Mapped unstructured address lines to Cdtr/PstlAdr/AdrLine"));
        }
    }

    /**
     * Maps MT103 Tag :70: (Remittance Information).
     * Guideline: Swift CBPR+ pacs.008 - Narrative lines map to RmtInf/Ustrd.
     */
    private void mapTag70(MT103Message mt103, CreditTransferTransaction73 txInf, List<MappingNote> notes) {
        Optional<RawTag> tag70Opt = mt103.tag70();
        if (tag70Opt.isEmpty()) {
            // Optional field :70: is not present in source message
            return;
        }

        RemittanceInformation26 rmtInf = objectFactory.createRemittanceInformation26();
        txInf.setRmtInf(rmtInf);

        List<String> lines = tag70Opt.get().lines();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                rmtInf.getUstrd().add(trimmed);
            }
        }
        notes.add(MappingNote.mapped(":70:", String.format("Mapped %d remittance narrative lines to RmtInf/Ustrd", lines.size())));
    }

    /**
     * Maps MT103 Tag :71A: (Details of Charges).
     * Guideline: Swift CBPR+ pacs.008 - OUR -> DEBT, BEN -> CRED, SHA -> SHAR.
     */
    private void mapTag71A(MT103Message mt103, CreditTransferTransaction73 txInf, List<MappingNote> notes) {
        Optional<RawTag> tag71AOpt = mt103.tag71A();
        if (tag71AOpt.isEmpty()) {
            notes.add(MappingNote.unmapped(":71A:", "Field :71A: is missing"));
            return;
        }

        String code = tag71AOpt.get().value().trim().toUpperCase(Locale.ROOT);
        switch (code) {
            // Guideline: Swift CBPR+ pacs.008 - OUR maps to ChargeBearer DEBT (borne by debtor)
            case "OUR" -> {
                txInf.setChrgBr(ChargeBearerType1Code.DEBT);
                notes.add(MappingNote.mapped(":71A:", "Mapped charge code 'OUR' to ChargeBearer 'DEBT'"));
            }
            // Guideline: Swift CBPR+ pacs.008 - BEN maps to ChargeBearer CRED (borne by creditor)
            case "BEN" -> {
                txInf.setChrgBr(ChargeBearerType1Code.CRED);
                notes.add(MappingNote.mapped(":71A:", "Mapped charge code 'BEN' to ChargeBearer 'CRED'"));
            }
            // Guideline: Swift CBPR+ pacs.008 - SHA maps to ChargeBearer SHAR (shared)
            case "SHA" -> {
                txInf.setChrgBr(ChargeBearerType1Code.SHAR);
                notes.add(MappingNote.mapped(":71A:", "Mapped charge code 'SHA' to ChargeBearer 'SHAR'"));
            }
            default -> {
                // ASSUMPTION - verify: Non-standard charge code defaulted to SHAR with audit warning note
                txInf.setChrgBr(ChargeBearerType1Code.SHAR);
                notes.add(MappingNote.assumed(":71A:", String.format("Unrecognized charge code '%s'; defaulted to 'SHAR'", code)));
            }
        }
    }

    /**
     * Maps Debtor Agent (DbtrAgt) from Block 1 Sender BIC and Creditor Agent (CdtrAgt) from Block 2 Receiver BIC.
     * Guideline: Swift CBPR+ pacs.008 Base - Instructing/Debtor Agent mapped from Basic Header Block 1;
     * Instructed/Creditor Agent mapped from Application Header Block 2.
     */
    private void mapFinancialInstitutions(MT103Message mt103, GroupHeader131 grpHdr, CreditTransferTransaction73 txInf, List<MappingNote> notes) {
        String b1 = mt103.headers().block1();
        String senderBic = extractBicFromBlock1(b1);
        if (senderBic != null) {
            BranchAndFinancialInstitutionIdentification8 dbtrAgt = createAgentWithBic(senderBic);
            txInf.setDbtrAgt(dbtrAgt);
            grpHdr.setInstgAgt(createAgentWithBic(senderBic));
            notes.add(MappingNote.mapped("Block1[SenderBIC]", "Mapped sender BIC to DbtrAgt and InstgAgt: " + senderBic));
        } else {
            notes.add(MappingNote.unmapped("Block1[SenderBIC]", "Block 1 sender BIC absent; DbtrAgt omitted (captured in schema validation)"));
        }

        String b2 = mt103.headers().block2();
        String receiverBic = extractBicFromBlock2(b2);
        if (receiverBic != null) {
            BranchAndFinancialInstitutionIdentification8 cdtrAgt = createAgentWithBic(receiverBic);
            txInf.setCdtrAgt(cdtrAgt);
            grpHdr.setInstdAgt(createAgentWithBic(receiverBic));
            notes.add(MappingNote.mapped("Block2[ReceiverBIC]", "Mapped receiver BIC to CdtrAgt and InstdAgt: " + receiverBic));
        } else {
            notes.add(MappingNote.unmapped("Block2[ReceiverBIC]", "Block 2 receiver BIC absent; CdtrAgt omitted (captured in schema validation)"));
        }
    }

    private BranchAndFinancialInstitutionIdentification8 createAgentWithBic(String bic) {
        BranchAndFinancialInstitutionIdentification8 agt = objectFactory.createBranchAndFinancialInstitutionIdentification8();
        FinancialInstitutionIdentification23 finInstnId = objectFactory.createFinancialInstitutionIdentification23();
        finInstnId.setBICFI(bic);
        agt.setFinInstnId(finInstnId);
        return agt;
    }

    private String extractBicFromBlock1(String b1) {
        if (b1 == null || b1.length() < 12) return null;
        // Swift LT address: chars 3..11 (8 chars) + branch chars 12..15
        String baseBic = b1.substring(3, Math.min(b1.length(), 11));
        String branch = (b1.length() >= 15) ? b1.substring(12, 15) : "XXX";
        return baseBic + branch;
    }

    private String extractBicFromBlock2(String b2) {
        if (b2 == null || b2.length() < 12) return null;
        // Swift App header: chars 4..12 (8 chars) + branch chars 13..16
        String baseBic = b2.substring(4, Math.min(b2.length(), 12));
        String branch = (b2.length() >= 16) ? b2.substring(13, 16) : "XXX";
        return baseBic + branch;
    }

    private String marshalDocument(Document document) {
        try {
            Marshaller marshaller = jaxbContext.createMarshaller();
            marshaller.setProperty(Marshaller.JAXB_FORMATTED_OUTPUT, Boolean.TRUE);
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");

            StringWriter writer = new StringWriter();
            marshaller.marshal(objectFactory.createDocument(document), writer);
            return writer.toString();
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to marshal JAXB pacs.008 Document into XML", e);
        }
    }
}
