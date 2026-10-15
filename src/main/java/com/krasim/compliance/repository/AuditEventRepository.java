package com.krasim.compliance.repository;

import com.krasim.compliance.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findTop100ByOrderByOccurredAtDescIdDesc();

    List<AuditEvent> findTop100ByActionOrderByOccurredAtDescIdDesc(String action);
}