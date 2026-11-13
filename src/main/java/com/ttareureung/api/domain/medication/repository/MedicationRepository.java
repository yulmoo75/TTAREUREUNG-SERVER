package com.ttareureung.api.domain.medication.repository;

import com.ttareureung.api.domain.medication.entity.Medication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MedicationRepository extends JpaRepository<Medication, Long> {
    List<Medication> findBySeniorProfileIdOrderByCreatedAtDesc(Long seniorProfileId);

    @Modifying(clearAutomatically = true)
    @Query("delete from MedicationLog l where l.medication.id = :medicationId")
    void deleteByMedicationId(@Param("medicationId") Long medicationId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Medication m where m.seniorProfileId = :seniorId")
    void deleteAllBySeniorProfileId(@Param("seniorId") Long seniorId);
}
