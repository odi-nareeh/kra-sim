package com.krasim.compliance.repository;

import com.krasim.compliance.entity.Taxpayer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxpayerRepository extends JpaRepository<Taxpayer, String> {
}