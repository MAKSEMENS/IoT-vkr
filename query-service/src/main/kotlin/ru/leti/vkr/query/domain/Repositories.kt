package ru.leti.vkr.query.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface RoomStateRepository : JpaRepository<RoomStateView, String>

interface SensorEventQueryRepository : JpaRepository<SensorEventView, Long> {
    fun findByRoomIdAndRecordedAtBetweenOrderByRecordedAtAsc(
        roomId: String,
        from: Instant,
        to: Instant,
        pageable: Pageable
    ): List<SensorEventView>
}

interface AlertQueryRepository : JpaRepository<AlertView, Long> {
    fun findByRoomIdOrderByTriggeredAtDesc(roomId: String, pageable: Pageable): List<AlertView>
    fun findByResolvedAtIsNullOrderByTriggeredAtDesc(pageable: Pageable): List<AlertView>
    fun findByRoomIdAndResolvedAtIsNullOrderByTriggeredAtDesc(roomId: String, pageable: Pageable): List<AlertView>
}
