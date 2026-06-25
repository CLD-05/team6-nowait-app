// domain/slot/repository/SlotRepository.java
package com.nowait.domain.slot.repository;

import com.nowait.domain.slot.entity.Slot;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findByRestaurantIdAndSlotDate(
        Long restaurantId, LocalDate slotDate
    );

    Optional<Slot> findByRestaurantIdAndSlotDateAndSlotTime(
        Long restaurantId, LocalDate slotDate, LocalTime slotTime
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.id = :id")
    Optional<Slot> findByIdWithLock(@Param("id") Long id);
}
