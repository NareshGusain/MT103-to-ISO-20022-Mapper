package com.example.mtiso.address;

import com.example.mtiso.api.ConversionResponse;
import com.example.mtiso.audit.AuditService;
import com.example.mtiso.audit.ConversionEntity;
import com.example.mtiso.mapper.ConversionService;
import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Coordinates generation, review, approval/rejection, and application of address suggestions.
 * Enforces banking audit trails for every human-in-the-loop decision and re-run.
 */
@Service
public class AddressSuggestionManager {

    private final AddressSuggestionRepository suggestionRepository;
    private final AuditService auditService;
    private final RuleBasedAddressSuggestionService ruleBasedSuggestionService;
    private final LlmAddressSuggestionService llmAddressSuggestionService;
    private final ConversionService conversionService;
    private final MT103Parser parser;

    @Autowired
    public AddressSuggestionManager(
            AddressSuggestionRepository suggestionRepository,
            AuditService auditService,
            RuleBasedAddressSuggestionService ruleBasedSuggestionService,
            LlmAddressSuggestionService llmAddressSuggestionService,
            ConversionService conversionService,
            MT103Parser parser
    ) {
        this.suggestionRepository = Objects.requireNonNull(suggestionRepository, "suggestionRepository must not be null");
        this.auditService = Objects.requireNonNull(auditService, "auditService must not be null");
        this.ruleBasedSuggestionService = Objects.requireNonNull(ruleBasedSuggestionService, "ruleBasedSuggestionService must not be null");
        this.llmAddressSuggestionService = Objects.requireNonNull(llmAddressSuggestionService, "llmAddressSuggestionService must not be null");
        this.conversionService = Objects.requireNonNull(conversionService, "conversionService must not be null");
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
    }

    /**
     * Generates address suggestions for free-text address lines in a stored conversion,
     * saving each suggestion in PENDING status.
     */
    @Transactional
    public List<AddressSuggestionResponse> generateSuggestions(UUID conversionId, String providerPreference) {
        ConversionEntity conversion = auditService.findEntityById(conversionId)
                .orElseThrow(() -> new IllegalArgumentException("Conversion not found with id: " + conversionId));

        MT103Message message = parser.parse(conversion.getInputText());
        AddressSuggestionService service = selectProvider(providerPreference);

        List<AddressSuggestionEntity> created = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // 1. Debtor (:50K:) address lines
        List<String> debtorAddressLines = extractDebtorAddressLines(message);
        if (!debtorAddressLines.isEmpty()) {
            AddressSuggestion suggestion = service.suggestAddress(debtorAddressLines);
            AddressSuggestionEntity entity = new AddressSuggestionEntity(
                    UUID.randomUUID(),
                    conversionId,
                    PartyType.DEBTOR,
                    String.join("\n", debtorAddressLines),
                    suggestion.getStreetValue(),
                    suggestion.street().confidence(),
                    suggestion.getTownValue(),
                    suggestion.town().confidence(),
                    suggestion.getPostCodeValue(),
                    suggestion.postCode().confidence(),
                    suggestion.getCountryValue(),
                    suggestion.country().confidence(),
                    SuggestionStatus.PENDING,
                    service.getProviderName(),
                    now
            );
            created.add(suggestionRepository.save(entity));
        }

        // 2. Creditor (:59:) address lines
        List<String> creditorAddressLines = extractCreditorAddressLines(message);
        if (!creditorAddressLines.isEmpty()) {
            AddressSuggestion suggestion = service.suggestAddress(creditorAddressLines);
            AddressSuggestionEntity entity = new AddressSuggestionEntity(
                    UUID.randomUUID(),
                    conversionId,
                    PartyType.CREDITOR,
                    String.join("\n", creditorAddressLines),
                    suggestion.getStreetValue(),
                    suggestion.street().confidence(),
                    suggestion.getTownValue(),
                    suggestion.town().confidence(),
                    suggestion.getPostCodeValue(),
                    suggestion.postCode().confidence(),
                    suggestion.getCountryValue(),
                    suggestion.country().confidence(),
                    SuggestionStatus.PENDING,
                    service.getProviderName(),
                    now
            );
            created.add(suggestionRepository.save(entity));
        }

        return created.stream().map(AddressSuggestionResponse::from).toList();
    }

    /**
     * Retrieves all address suggestions for a conversion.
     */
    @Transactional(readOnly = true)
    public List<AddressSuggestionResponse> getSuggestions(UUID conversionId) {
        return suggestionRepository.findByConversionId(conversionId).stream()
                .map(AddressSuggestionResponse::from)
                .toList();
    }

    /**
     * Approves an address suggestion, updating its status and logging the decision in the audit trail.
     */
    @Transactional
    public AddressSuggestionResponse approveSuggestion(UUID suggestionId, String actor, String note) {
        AddressSuggestionEntity entity = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found with id: " + suggestionId));

        entity.setStatus(SuggestionStatus.APPROVED);
        entity.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setDecidedBy(actor);
        entity.setDecisionNote(note);

        AddressSuggestionEntity saved = suggestionRepository.save(entity);

        // Record in the central approvals audit log
        auditService.recordApproval(
                saved.getConversionId(),
                "ADDRESS_SUGGESTION_APPROVED",
                actor,
                String.format("Approved %s suggestion [%s] (Street: '%s', Town: '%s', PostCode: '%s', Country: '%s'): %s",
                        saved.getPartyType(), saved.getId(), saved.getStreet(), saved.getTown(), saved.getPostCode(), saved.getCountry(), note != null ? note : "")
        );

        return AddressSuggestionResponse.from(saved);
    }

    /**
     * Rejects an address suggestion, updating its status and logging the decision in the audit trail.
     */
    @Transactional
    public AddressSuggestionResponse rejectSuggestion(UUID suggestionId, String actor, String note) {
        AddressSuggestionEntity entity = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found with id: " + suggestionId));

        entity.setStatus(SuggestionStatus.REJECTED);
        entity.setDecidedAt(OffsetDateTime.now(ZoneOffset.UTC));
        entity.setDecidedBy(actor);
        entity.setDecisionNote(note);

        AddressSuggestionEntity saved = suggestionRepository.save(entity);

        // Record in the central approvals audit log
        auditService.recordApproval(
                saved.getConversionId(),
                "ADDRESS_SUGGESTION_REJECTED",
                actor,
                String.format("Rejected %s suggestion [%s]: %s", saved.getPartyType(), saved.getId(), note != null ? note : "")
        );

        return AddressSuggestionResponse.from(saved);
    }

    /**
     * Re-runs the conversion applying only APPROVED address suggestions.
     */
    @Transactional
    public ConversionResponse rerunConversion(UUID conversionId) {
        List<AddressSuggestionEntity> approvedEntities = suggestionRepository
                .findByConversionIdAndStatus(conversionId, SuggestionStatus.APPROVED);

        Map<PartyType, AddressSuggestion> approvedMap = new EnumMap<>(PartyType.class);
        for (AddressSuggestionEntity entity : approvedEntities) {
            approvedMap.put(entity.getPartyType(), entity.toAddressSuggestion());
        }

        ConversionResponse response = conversionService.reconvert(conversionId, approvedMap);

        // Record re-run action in audit log
        auditService.recordApproval(
                conversionId,
                "CONVERSION_RERUN",
                "SYSTEM",
                String.format("Re-ran conversion with %d approved address suggestion(s); upgraded verdict: %s",
                        approvedMap.size(), response.report().verdict())
        );

        return response;
    }

    private AddressSuggestionService selectProvider(String providerPreference) {
        if ("LLM".equalsIgnoreCase(providerPreference)) {
            return llmAddressSuggestionService;
        }
        return ruleBasedSuggestionService;
    }

    public static List<String> extractDebtorAddressLines(MT103Message message) {
        return message.tag50K().map(tag -> {
            List<String> lines = tag.lines();
            if (lines.isEmpty()) return Collections.<String>emptyList();
            boolean hasAcct = lines.get(0).trim().startsWith("/");
            int addrStart = hasAcct ? 2 : 1;
            if (lines.size() <= addrStart) return Collections.<String>emptyList();
            return lines.subList(addrStart, lines.size());
        }).orElse(Collections.emptyList());
    }

    public static List<String> extractCreditorAddressLines(MT103Message message) {
        return message.tag59().map(tag -> {
            List<String> lines = tag.lines();
            if (lines.isEmpty()) return Collections.<String>emptyList();
            boolean hasAcct = lines.get(0).trim().startsWith("/");
            int addrStart = hasAcct ? 2 : 1;
            if (lines.size() <= addrStart) return Collections.<String>emptyList();
            return lines.subList(addrStart, lines.size());
        }).orElse(Collections.emptyList());
    }
}

