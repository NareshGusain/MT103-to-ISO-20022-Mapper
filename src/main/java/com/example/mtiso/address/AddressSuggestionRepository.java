package com.example.mtiso.address;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AddressSuggestionRepository extends JpaRepository<AddressSuggestionEntity, UUID> {

    List<AddressSuggestionEntity> findByConversionId(UUID conversionId);

    List<AddressSuggestionEntity> findByConversionIdAndStatus(UUID conversionId, SuggestionStatus status);

    Optional<AddressSuggestionEntity> findByConversionIdAndPartyTypeAndStatus(
            UUID conversionId,
            PartyType partyType,
            SuggestionStatus status
    );
}

