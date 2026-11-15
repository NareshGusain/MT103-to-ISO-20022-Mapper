package com.example.mtiso.mapper;

import com.example.mtiso.iso.pacs008.*;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import com.example.mtiso.parser.RawTag;
import com.example.mtiso.parser.SwiftHeaders;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import org.springframework.stereotype.Component;

import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.transform.stream.StreamSource;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.*;

/**
 * Reverse mapper: Transforms ISO 20022 pacs.008.001.14 XML / Document back into
 * an MT103 representation for the supported core banking fields.
 *
 * Supported Fields:
 * - Block 1 & 2 headers from DbtrAgt and CdtrAgt BICs
 * - :20: from PmtId/InstrId (or EndToEndId / TxId / GrpHdr/MsgId)
 * - :23B: standard CRED (Customer Credit Transfer)
 * - :32A: from IntrBkSttlmDt and IntrBkSttlmAmt
 * - :50K: from DbtrAcct and Dbtr (name and unstructured address lines)
 * - :59: from CdtrAcct and Cdtr (name and unstructured address lines)
 * - :70: from RmtInf/Ustrd
 * - :71A: from ChrgBr (DEBT -> OUR, CRED -> BEN, SHAR -> SHA)
 */
@Component
public class Pacs008ToMt103 {

    private final MT103Parser parser;
    private final JAXBContext jaxbContext;

    public Pacs008ToMt103(MT103Parser parser) {
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        try {
            this.jaxbContext = JAXBContext.newInstance(Document.class);
        } catch (JAXBException e) {
            throw new IllegalStateException("Failed to initialize JAXBContext for pacs.008 reverse mapper", e);
        }
    }

    /**
     * Converts a JAXB Document into an MT103Message.
     */
    public MT103Message toMt103(Document document) {
        String rawText = toMt103Text(document);
        return parser.parse(rawText);
    }

    /**
     * Converts a raw pacs.008 XML string into an MT103Message.
     */
    public MT103Message toMt103(String pacs008Xml) {
        Document document = unmarshal(pacs008Xml);
        return toMt103(document);
    }

    /**
     * Converts a raw pacs.008 XML string into formatted SWIFT MT103 FIN text.
     */
    public String toMt103Text(String pacs008Xml) {
        Document document = unmarshal(pacs008Xml);
        return toMt103Text(document);
    }

    /**
     * Serializes a pacs.008 Document into standard SWIFT MT103 FIN text for supported fields.
     */
    public String toMt103Text(Document document) {
        Objects.requireNonNull(document, "document must not be null");
        FIToFICustomerCreditTransferV14 cdtTrf = document.getFIToFICstmrCdtTrf();
        if (cdtTrf == null) {
            throw new IllegalArgumentException("Document does not contain FIToFICstmrCdtTrf");
        }

        GroupHeader131 grpHdr = cdtTrf.getGrpHdr();
        CreditTransferTransaction73 txInf = cdtTrf.getCdtTrfTxInf().isEmpty() ? null : cdtTrf.getCdtTrfTxInf().getFirst();

        StringBuilder sb = new StringBuilder();

        // 1. Reconstruct Envelope Headers (Blocks 1 & 2)
        // Guideline: Swift CBPR+ pacs.008 - DbtrAgt and CdtrAgt BICs map back to MT103 Block 1 and Block 2 headers
        BranchAndFinancialInstitutionIdentification8 dbtrAgt = null;
        BranchAndFinancialInstitutionIdentification8 cdtrAgt = null;

        if (txInf != null) {
            dbtrAgt = txInf.getDbtrAgt();
            cdtrAgt = txInf.getCdtrAgt();
        }
        if (dbtrAgt == null && grpHdr != null) {
            dbtrAgt = grpHdr.getInstgAgt();
        }
        if (cdtrAgt == null && grpHdr != null) {
            cdtrAgt = grpHdr.getInstdAgt();
        }

        String senderBic = extractBic(dbtrAgt);
        String receiverBic = extractBic(cdtrAgt);

        if (senderBic != null) {
            String senderLt = formatSenderLtAddress(senderBic);
            sb.append("{1:F01").append(senderLt).append("0000000000}");
        }
        if (receiverBic != null) {
            String receiverLt = formatReceiverLtAddress(receiverBic);
            sb.append("{2:I103").append(receiverLt).append("N}");
        }

        // Block 4 start
        sb.append("{4:\r\n");

        if (txInf != null) {
            // 2. Tag :20: (Transaction Reference Number)
            // Guideline: Swift CBPR+ pacs.008 - PmtId/InstrId maps back to MT103 Field :20:
            String ref = extractReference(grpHdr, txInf);
            if (ref != null && !ref.isBlank() && !ref.equals("NOTPROVIDED")) {
                sb.append(":20:").append(ref).append("\r\n");
            }

            // 3. Tag :23B: (Bank Operation Code)
            // Guideline: Swift CBPR+ pacs.008 - FIToFICustomerCreditTransfer represents standard credit transfer, mapping back to :23B:CRED
            sb.append(":23B:CRED\r\n");

            // 4. Tag :32A: (Value Date, Currency, Amount)
            // Guideline: Swift CBPR+ pacs.008 - IntrBkSttlmDt and IntrBkSttlmAmt map back to MT103 Field :32A: (YYMMDDCcyAmount)
            String tag32AValue = extractTag32A(grpHdr, txInf);
            if (tag32AValue != null) {
                sb.append(":32A:").append(tag32AValue).append("\r\n");
            }

            // 5. Tag :50K: (Ordering Customer)
            // Guideline: Swift CBPR+ pacs.008 - DbtrAcct, Dbtr/Nm, and Dbtr/PstlAdr/AdrLine map back to MT103 Field :50K:
            String tag50KValue = extractParty50K(txInf);
            if (tag50KValue != null) {
                sb.append(":50K:").append(tag50KValue).append("\r\n");
            }

            // 6. Tag :59: (Beneficiary Customer)
            // Guideline: Swift CBPR+ pacs.008 - CdtrAcct, Cdtr/Nm, and Cdtr/PstlAdr/AdrLine map back to MT103 Field :59:
            String tag59Value = extractParty59(txInf);
            if (tag59Value != null) {
                sb.append(":59:").append(tag59Value).append("\r\n");
            }

            // 7. Tag :70: (Remittance Information)
            // Guideline: Swift CBPR+ pacs.008 - RmtInf/Ustrd lines map back to MT103 Field :70:
            String tag70Value = extractTag70(txInf);
            if (tag70Value != null) {
                sb.append(":70:").append(tag70Value).append("\r\n");
            }

            // 8. Tag :71A: (Details of Charges)
            // Guideline: Swift CBPR+ pacs.008 - ChrgBr DEBT/CRED/SHAR maps back to MT103 Field :71A: OUR/BEN/SHA
            String tag71AValue = extractTag71A(txInf);
            if (tag71AValue != null) {
                sb.append(":71A:").append(tag71AValue).append("\r\n");
            }
        }

        // Block 4 end
        sb.append("-}\r\n");

        return sb.toString();
    }

    private Document unmarshal(String xml) {
        try {
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            JAXBElement<Document> element = unmarshaller.unmarshal(new StreamSource(new StringReader(xml)), Document.class);
            return element.getValue();
        } catch (JAXBException e) {
            throw new IllegalArgumentException("Failed to unmarshal pacs.008 XML", e);
        }
    }

    private String extractReference(GroupHeader131 grpHdr, CreditTransferTransaction73 txInf) {
        if (txInf.getPmtId() != null) {
            if (txInf.getPmtId().getInstrId() != null && !txInf.getPmtId().getInstrId().isBlank()) {
                return txInf.getPmtId().getInstrId().trim();
            }
            if (txInf.getPmtId().getEndToEndId() != null && !txInf.getPmtId().getEndToEndId().isBlank()) {
                return txInf.getPmtId().getEndToEndId().trim();
            }
            if (txInf.getPmtId().getTxId() != null && !txInf.getPmtId().getTxId().isBlank()) {
                return txInf.getPmtId().getTxId().trim();
            }
        }
        if (grpHdr != null && grpHdr.getMsgId() != null) {
            return grpHdr.getMsgId().trim();
        }
        return null;
    }

    private String extractTag32A(GroupHeader131 grpHdr, CreditTransferTransaction73 txInf) {
        XMLGregorianCalendar sttlmDt = txInf.getIntrBkSttlmDt();
        if (sttlmDt == null && grpHdr != null) {
            sttlmDt = grpHdr.getIntrBkSttlmDt();
        }
        ActiveCurrencyAndAmount sttlmAmt = txInf.getIntrBkSttlmAmt();

        if (sttlmDt == null || sttlmAmt == null || sttlmAmt.getCcy() == null || sttlmAmt.getValue() == null) {
            return null;
        }

        int year = Math.abs(sttlmDt.getYear()) % 100;
        int month = sttlmDt.getMonth();
        int day = sttlmDt.getDay();
        String dateStr = String.format(Locale.ROOT, "%02d%02d%02d", year, month, day);

        String ccy = sttlmAmt.getCcy().trim().toUpperCase(Locale.ROOT);
        BigDecimal amt = sttlmAmt.getValue();
        String amtStr = amt.toPlainString();
        if (amtStr.contains(".")) {
            amtStr = amtStr.replace('.', ',');
        } else {
            amtStr = amtStr + ",";
        }

        return dateStr + ccy + amtStr;
    }

    private String extractParty50K(CreditTransferTransaction73 txInf) {
        List<String> lines = new ArrayList<>();

        if (txInf.getDbtrAcct() != null && txInf.getDbtrAcct().getId() != null) {
            AccountIdentification4Choice id = txInf.getDbtrAcct().getId();
            if (id.getIBAN() != null && !id.getIBAN().isBlank()) {
                lines.add("/" + id.getIBAN().trim());
            } else if (id.getOthr() != null && id.getOthr().getId() != null && !id.getOthr().getId().isBlank()) {
                lines.add("/" + id.getOthr().getId().trim());
            }
        }

        if (txInf.getDbtr() != null) {
            if (txInf.getDbtr().getNm() != null && !txInf.getDbtr().getNm().isBlank()) {
                lines.add(txInf.getDbtr().getNm().trim());
            }
            if (txInf.getDbtr().getPstlAdr() != null) {
                var pstlAdr = txInf.getDbtr().getPstlAdr();
                if (pstlAdr.getAdrLine() != null && !pstlAdr.getAdrLine().isEmpty()) {
                    for (String adr : pstlAdr.getAdrLine()) {
                        if (adr != null && !adr.isBlank()) {
                            lines.add(adr.trim());
                        }
                    }
                } else {
                    if (pstlAdr.getStrtNm() != null && !pstlAdr.getStrtNm().isBlank()) {
                        lines.add(pstlAdr.getStrtNm().trim());
                    }
                    StringBuilder loc = new StringBuilder();
                    if (pstlAdr.getPstCd() != null && !pstlAdr.getPstCd().isBlank()) {
                        loc.append(pstlAdr.getPstCd().trim()).append(" ");
                    }
                    if (pstlAdr.getTwnNm() != null && !pstlAdr.getTwnNm().isBlank()) {
                        loc.append(pstlAdr.getTwnNm().trim());
                    }
                    if (pstlAdr.getCtry() != null && !pstlAdr.getCtry().isBlank()) {
                        loc.append(", ").append(pstlAdr.getCtry().trim());
                    }
                    if (!loc.isEmpty()) {
                        lines.add(loc.toString().trim());
                    }
                }
            }
        }

        return lines.isEmpty() ? null : String.join("\r\n", lines);
    }

    private String extractParty59(CreditTransferTransaction73 txInf) {
        List<String> lines = new ArrayList<>();

        if (txInf.getCdtrAcct() != null && txInf.getCdtrAcct().getId() != null) {
            AccountIdentification4Choice id = txInf.getCdtrAcct().getId();
            if (id.getIBAN() != null && !id.getIBAN().isBlank()) {
                lines.add("/" + id.getIBAN().trim());
            } else if (id.getOthr() != null && id.getOthr().getId() != null && !id.getOthr().getId().isBlank()) {
                lines.add("/" + id.getOthr().getId().trim());
            }
        }

        if (txInf.getCdtr() != null) {
            if (txInf.getCdtr().getNm() != null && !txInf.getCdtr().getNm().isBlank()) {
                lines.add(txInf.getCdtr().getNm().trim());
            }
            if (txInf.getCdtr().getPstlAdr() != null) {
                var pstlAdr = txInf.getCdtr().getPstlAdr();
                if (pstlAdr.getAdrLine() != null && !pstlAdr.getAdrLine().isEmpty()) {
                    for (String adr : pstlAdr.getAdrLine()) {
                        if (adr != null && !adr.isBlank()) {
                            lines.add(adr.trim());
                        }
                    }
                } else {
                    if (pstlAdr.getStrtNm() != null && !pstlAdr.getStrtNm().isBlank()) {
                        lines.add(pstlAdr.getStrtNm().trim());
                    }
                    StringBuilder loc = new StringBuilder();
                    if (pstlAdr.getPstCd() != null && !pstlAdr.getPstCd().isBlank()) {
                        loc.append(pstlAdr.getPstCd().trim()).append(" ");
                    }
                    if (pstlAdr.getTwnNm() != null && !pstlAdr.getTwnNm().isBlank()) {
                        loc.append(pstlAdr.getTwnNm().trim());
                    }
                    if (pstlAdr.getCtry() != null && !pstlAdr.getCtry().isBlank()) {
                        loc.append(", ").append(pstlAdr.getCtry().trim());
                    }
                    if (!loc.isEmpty()) {
                        lines.add(loc.toString().trim());
                    }
                }
            }
        }

        return lines.isEmpty() ? null : String.join("\r\n", lines);
    }

    private String extractTag70(CreditTransferTransaction73 txInf) {
        if (txInf.getRmtInf() == null || txInf.getRmtInf().getUstrd() == null || txInf.getRmtInf().getUstrd().isEmpty()) {
            return null;
        }

        List<String> lines = new ArrayList<>();
        for (String ustrd : txInf.getRmtInf().getUstrd()) {
            if (ustrd != null && !ustrd.isBlank()) {
                lines.add(ustrd.trim());
            }
        }
        return lines.isEmpty() ? null : String.join("\r\n", lines);
    }

    private String extractTag71A(CreditTransferTransaction73 txInf) {
        if (txInf.getChrgBr() == null) {
            return null;
        }
        return switch (txInf.getChrgBr()) {
            case DEBT -> "OUR";
            case CRED -> "BEN";
            case SHAR -> "SHA";
            // ASSUMPTION - verify: SLEV (Service Level) charge code has no direct MT103 representation, mapped to SHA
            case SLEV -> "SHA";
        };
    }

    private String extractBic(BranchAndFinancialInstitutionIdentification8 fi) {
        if (fi == null || fi.getFinInstnId() == null) {
            return null;
        }
        return fi.getFinInstnId().getBICFI();
    }

    /**
     * Converts a sender BIC into a 12-character SWIFT Block 1 Logical Terminal (LT) address.
     * Character 9 is 'A' (primary terminal) and 10-12 is branch code ('XXX' default).
     */
    private String formatSenderLtAddress(String bic) {
        if (bic == null || bic.isBlank()) {
            return "XXXXXXXXXXXX";
        }
        String clean = bic.trim().toUpperCase(Locale.ROOT);
        if (clean.length() == 8) {
            return clean + "AXXX";
        } else if (clean.length() == 11) {
            return clean.substring(0, 8) + "A" + clean.substring(8);
        } else if (clean.length() >= 12) {
            return clean.substring(0, 12);
        } else {
            return String.format("%-12s", clean).replace(' ', 'X');
        }
    }

    /**
     * Converts a receiver BIC into a 12-character SWIFT Block 2 Input Logical Terminal (LT) address.
     * In SWIFT Input messages (I103), the LT code at position 9 is typically 'X' (e.g. BANKDEFFXXXX).
     */
    private String formatReceiverLtAddress(String bic) {
        if (bic == null || bic.isBlank()) {
            return "XXXXXXXXXXXX";
        }
        String clean = bic.trim().toUpperCase(Locale.ROOT);
        if (clean.length() == 8) {
            return clean + "XXXX";
        } else if (clean.length() == 11) {
            return clean.substring(0, 8) + "X" + clean.substring(8);
        } else if (clean.length() >= 12) {
            return clean.substring(0, 12);
        } else {
            return String.format("%-12s", clean).replace(' ', 'X');
        }
    }
}
