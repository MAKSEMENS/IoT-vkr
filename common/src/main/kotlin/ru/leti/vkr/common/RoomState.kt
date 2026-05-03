package ru.leti.vkr.common

import java.time.Instant

data class RoomState(
    val roomId: String,
    val readings: Map<SensorType, Double>,
    val updatedAt: Instant,
    val version: Long
)
