package ru.leti.vkr.aggregation.domain

import org.springframework.data.jpa.repository.JpaRepository

interface RoomStateRepository : JpaRepository<RoomStateEntity, String>
