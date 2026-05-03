package ru.leti.vkr.query.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.leti.vkr.common.SensorType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sensor_events")
class SensorEventView(
    @Id
    val id: Long,

    @Column(name = "event_id", nullable = false)
    val eventId: UUID,

    @Column(name = "room_id", nullable = false)
    val roomId: String,

    @Column(name = "sensor_id", nullable = false)
    val sensorId: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "sensor_type", nullable = false)
    val sensorType: SensorType,

    @Column(name = "value", nullable = false)
    val value: Double,

    @Column(name = "recorded_at", nullable = false)
    val recordedAt: Instant
)
