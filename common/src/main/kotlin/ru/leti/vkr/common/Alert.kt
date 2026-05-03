package ru.leti.vkr.common

import java.time.Instant
import java.util.UUID

data class Alert(
    val alertId: UUID,
    val roomId: String,
    val sensorType: SensorType,
    val ruleName: String,
    val severity: AlertSeverity,
    val message: String,
    val triggeringValue: Double,
    val triggeredAt: Instant
)
