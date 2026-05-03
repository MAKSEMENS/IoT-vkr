package ru.leti.vkr.ingestion.api

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import ru.leti.vkr.common.SensorType
import java.time.Instant

data class SensorEventRequest(
    @field:NotBlank val roomId: String,
    @field:NotBlank val sensorId: String,
    @field:NotNull val sensorType: SensorType,
    @field:NotNull val value: Double,
    val timestamp: Instant? = null
)
