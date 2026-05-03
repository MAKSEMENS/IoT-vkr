package ru.leti.vkr.aggregation.domain

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID
import java.util.stream.Stream

interface SensorEventRepository : JpaRepository<SensorEventEntity, Long> {
    fun existsByEventId(eventId: UUID): Boolean

    @Query("SELECT e FROM SensorEventEntity e ORDER BY e.recordedAt ASC, e.id ASC")
    fun streamAllOrderedByTime(): Stream<SensorEventEntity>
}
