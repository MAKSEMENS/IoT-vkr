package ru.leti.vkr.aggregation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import ru.leti.vkr.common.SensorType
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "sensor_events")
class SensorEventEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "event_id", nullable = false, unique = true)
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
