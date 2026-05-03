package ru.leti.vkr.query.domain

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RoomStateRepository : JpaRepository<RoomStateView, String>

interface SensorEventQueryRepository : JpaRepository<SensorEventView, Long> {
    fun findByRoomIdOrderByRecordedAtDesc(roomId: String, pageable: Pageable): List<SensorEventView>
}

interface AlertQueryRepository : JpaRepository<AlertView, Long> {
    fun findByRoomIdOrderByTriggeredAtDesc(roomId: String, pageable: Pageable): List<AlertView>
    fun findByResolvedAtIsNullOrderByTriggeredAtDesc(pageable: Pageable): List<AlertView>
}
