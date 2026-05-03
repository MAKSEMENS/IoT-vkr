package ru.leti.vkr.common

import java.time.Instant
import java.util.UUID

data class SensorEvent(
    val eventId: UUID,
    val roomId: String,
    val sensorId: String,
    val sensorType: SensorType,
    val value: Double,
    val timestamp: Instant
)
