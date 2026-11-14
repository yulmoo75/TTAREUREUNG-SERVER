package com.ttareureung.api.domain.medication.repository;

import com.ttareureung.api.domain.medication.entity.MedicationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface MedicationLogRepository extends JpaRepository<MedicationLog, Long> {

    @Query("""
            select l from MedicationLog l
            join fetch l.medication m
            where m.seniorProfileId = :seniorId
              and l.targetDate = :date
            """)
    List<MedicationLog> findDailyLogs(@Param("seniorId") Long seniorId,
                                      @Param("date") LocalDate date);

    @Modifying(flushAutomatically = true)
    @Query("delete from MedicationLog l where l.medication.id in :ids")
    void deleteByMedicationIds(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true)
    @Query("delete from MedicationLog l where l.medication.id in "
            + "(select m.id from Medication m where m.seniorProfileId = :seniorId)")
    void deleteAllBySeniorProfileId(@Param("seniorId") Long seniorId);

    @Query("select distinct l.medication.id, l.mealTimeSlot from MedicationLog l where l.medication.id in :ids")
    List<Object[]> findSlotsByMedicationIds(@Param("ids") Collection<Long> ids);

    @Query("""
        select l from MedicationLog l join fetch l.medication m
        where m.seniorProfileId = :seniorId and l.targetDate between :from and :to
        order by l.targetDate
        """)
    List<MedicationLog> findMonthlyLogs(@Param("seniorId") Long seniorId,
                                        @Param("from") LocalDate from, @Param("to") LocalDate to);
}