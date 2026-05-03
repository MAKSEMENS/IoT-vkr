package ru.leti.vkr.aggregation.domain

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SensorEventRepository : JpaRepository<SensorEventEntity, Long> {
    fun existsByEventId(eventId: UUID): Boolean
}
