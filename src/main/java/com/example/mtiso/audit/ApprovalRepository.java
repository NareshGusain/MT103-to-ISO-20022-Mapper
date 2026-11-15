package com.example.mtiso.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApprovalRepository extends JpaRepository<ApprovalEntity, UUID> {

    List<ApprovalEntity> findByConversionId(UUID conversionId);
}

