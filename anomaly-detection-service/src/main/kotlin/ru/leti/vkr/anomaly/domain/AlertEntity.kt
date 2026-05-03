package ru.leti.vkr.anomaly.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.leti.vkr.common.AlertSeverity
import ru.leti.vkr.common.SensorType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "alerts")
class AlertEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "alert_id", nullable = false, unique = true)
    val alertId: UUID,

    @Column(name = "room_id", nullable = false)
    val roomId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false)
    val sensorType: SensorType,

    @Column(name = "rule_name", nullable = false)
    val ruleName: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    val severity: AlertSeverity,

    @Column(name = "message", nullable = false)
    val message: String,

    @Column(name = "triggering_value", nullable = false)
    val triggeringValue: Double,

    @Column(name = "triggered_at", nullable = false)
    val triggeredAt: Instant,

    @Column(name = "resolved_at")
    var resolvedAt: Instant? = null
)
