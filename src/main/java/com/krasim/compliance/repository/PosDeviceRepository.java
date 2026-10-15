package com.krasim.compliance.repository;

import com.krasim.compliance.entity.PosDevice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface PosDeviceRepository extends JpaRepository<PosDevice, Integer> {

    Optional<PosDevice> findByKeyPrefix(String keyPrefix);

    boolean existsByKeyPrefix(String keyPrefix);

    List<PosDevice> findAllByOrderByIdAsc();

    /**
     * SELECT ... FOR UPDATE: serialises the invoice counter of one device (used in
     * Part 3).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PosDevice> findForUpdateById(Integer id);
}