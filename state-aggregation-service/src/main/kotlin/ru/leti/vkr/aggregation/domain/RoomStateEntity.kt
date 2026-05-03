package ru.leti.vkr.aggregation.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.Version
import java.time.Instant

@Entity
@Table(name = "room_states")
class RoomStateEntity(
    @Id
    @Column(name = "room_id")
    val roomId: String,

    @Column(name = "temperature")
    var temperature: Double? = null,

    @Column(name = "humidity")
    var humidity: Double? = null,

    @Column(name = "co2")
    var co2: Double? = null,

    @Column(name = "smoke")
    var smoke: Double? = null,

    @Column(name = "motion")
    var motion: Double? = null,

    @Column(name = "light")
    var light: Double? = null,

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.EPOCH,

    @Version
    @Column(name = "version", nullable = false)
    var version: Long = 0
)
