package com.krasim.compliance.repository;

import com.krasim.compliance.entity.TaxLedger;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface TaxLedgerRepository extends JpaRepository<TaxLedger, Integer> {

    /** Plain read, used by the dashboard. */
    Optional<TaxLedger> findByTaxpayerPin(String pin);

    /**
     * Locks the ledger row (SELECT ... FOR UPDATE) so two invoices for the same PIN
     * arriving at the same moment cannot overwrite each other's VAT increment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaxLedger> findForUpdateByTaxpayerPin(String pin);
}