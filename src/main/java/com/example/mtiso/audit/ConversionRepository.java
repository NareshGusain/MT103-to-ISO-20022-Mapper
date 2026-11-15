package com.example.mtiso.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConversionRepository extends JpaRepository<ConversionEntity, UUID> {

    List<ConversionEntity> findByInputHash(String inputHash);

    List<ConversionEntity> findByStatus(String status);
}

