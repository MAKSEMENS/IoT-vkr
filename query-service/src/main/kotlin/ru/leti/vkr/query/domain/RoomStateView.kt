package ru.leti.vkr.query.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "room_states")
class RoomStateView(
    @Id
    @Column(name = "room_id")
    val roomId: String,

    @Column(name = "temperature") val temperature: Double? = null,
    @Column(name = "humidity") val humidity: Double? = null,
    @Column(name = "co2") val co2: Double? = null,
    @Column(name = "smoke") val smoke: Double? = null,
    @Column(name = "motion") val motion: Double? = null,
    @Column(name = "light") val light: Double? = null,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.EPOCH,

    @Column(name = "version", nullable = false)
    val version: Long = 0
)
