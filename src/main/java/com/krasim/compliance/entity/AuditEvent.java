package com.krasim.compliance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One immutable audit record. There are no setters: events are only ever
 * created.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "actor", nullable = false, updatable = false, length = 60)
    private String actor;

    @Column(name = "action", nullable = false, updatable = false, length = 40)
    private String action;

    @Column(name = "target", updatable = false, length = 100)
    private String target;

    @Column(name = "details", updatable = false, length = 300)
    private String details;

    @Column(name = "source_ip", updatable = false, length = 45)
    private String sourceIp;

    protected AuditEvent() {
        // required by JPA
    }

    public AuditEvent(String actor, String action, String target, String details, String sourceIp) {
        this.occurredAt = LocalDateTime.now();
        this.actor = actor;
        this.action = action;
        this.target = target;
        this.details = details;
        this.sourceIp = sourceIp;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    public String getDetails() {
        return details;
    }

    public String getSourceIp() {
        return sourceIp;
    }
}