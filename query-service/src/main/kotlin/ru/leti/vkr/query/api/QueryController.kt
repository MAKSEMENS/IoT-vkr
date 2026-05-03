package ru.leti.vkr.query.api

import org.springframework.data.domain.PageRequest
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.leti.vkr.common.AlertSeverity
import ru.leti.vkr.common.SensorType
import ru.leti.vkr.query.domain.AlertQueryRepository
import ru.leti.vkr.query.domain.AlertView
import ru.leti.vkr.query.domain.RoomStateRepository
import ru.leti.vkr.query.domain.RoomStateView
import ru.leti.vkr.query.domain.SensorEventQueryRepository
import ru.leti.vkr.query.domain.SensorEventView
import java.time.Instant
import java.util.UUID

@RestController
@RequestMapping
class QueryController(
    private val rooms: RoomStateRepository,
    private val events: SensorEventQueryRepository,
    private val alerts: AlertQueryRepository
) {
    @GetMapping("/rooms")
    fun listRooms(): List<RoomStateResponse> =
        rooms.findAll().map { it.toResponse() }

    @GetMapping("/rooms/{roomId}")
    fun roomState(@PathVariable roomId: String): ResponseEntity<RoomStateResponse> =
        rooms.findById(roomId)
            .map { ResponseEntity.ok(it.toResponse()) }
            .orElse(ResponseEntity.notFound().build())

    @GetMapping("/rooms/{roomId}/history")
    fun roomHistory(
        @PathVariable roomId: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant,
        @RequestParam(defaultValue = "1000") limit: Int
    ): List<SensorEventResponse> =
        events.findByRoomIdAndRecordedAtBetweenOrderByRecordedAtAsc(
            roomId, from, to, PageRequest.of(0, limit.coerceIn(1, 10_000))
        ).map { it.toResponse() }

    @GetMapping("/alerts")
    fun alertsList(
        @RequestParam(required = false) roomId: String?,
        @RequestParam(defaultValue = "false") active: Boolean,
        @RequestParam(defaultValue = "100") limit: Int
    ): List<AlertResponse> {
        val page = PageRequest.of(0, limit.coerceIn(1, 1000))
        val result = when {
            roomId != null && active -> alerts.findByRoomIdAndResolvedAtIsNullOrderByTriggeredAtDesc(roomId, page)
            roomId != null -> alerts.findByRoomIdOrderByTriggeredAtDesc(roomId, page)
            active -> alerts.findByResolvedAtIsNullOrderByTriggeredAtDesc(page)
            else -> alerts.findAll(page).content
        }
        return result.map { it.toResponse() }
    }
}

data class RoomStateResponse(
    val roomId: String,
    val readings: Map<SensorType, Double>,
    val updatedAt: Instant,
    val version: Long
)

data class SensorEventResponse(
    val eventId: UUID,
    val roomId: String,
    val sensorId: String,
    val sensorType: SensorType,
    val value: Double,
    val recordedAt: Instant
)

data class AlertResponse(
    val alertId: UUID,
    val roomId: String,
    val sensorType: SensorType,
    val ruleName: String,
    val severity: AlertSeverity,
    val message: String,
    val triggeringValue: Double,
    val triggeredAt: Instant,
    val resolvedAt: Instant?
)

private fun RoomStateView.toResponse(): RoomStateResponse {
    val readings = buildMap<SensorType, Double> {
        temperature?.let { put(SensorType.TEMPERATURE, it) }
        humidity?.let { put(SensorType.HUMIDITY, it) }
        co2?.let { put(SensorType.CO2, it) }
        smoke?.let { put(SensorType.SMOKE, it) }
        motion?.let { put(SensorType.MOTION, it) }
        light?.let { put(SensorType.LIGHT, it) }
    }
    return RoomStateResponse(roomId, readings, updatedAt, version)
}

private fun SensorEventView.toResponse() =
    SensorEventResponse(eventId, roomId, sensorId, sensorType, value, recordedAt)

private fun AlertView.toResponse() =
    AlertResponse(alertId, roomId, sensorType, ruleName, severity, message, triggeringValue, triggeredAt, resolvedAt)
