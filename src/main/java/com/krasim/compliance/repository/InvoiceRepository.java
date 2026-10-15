package com.krasim.compliance.repository;

import com.krasim.compliance.entity.Invoice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

    long countByTaxpayerPin(String pin);

    @EntityGraph(attributePaths = "device")
    List<Invoice> findTop10ByTaxpayerPinOrderByCreatedAtDesc(String pin);
}