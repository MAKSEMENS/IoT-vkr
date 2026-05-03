package ru.leti.vkr.anomaly.domain

import org.springframework.data.jpa.repository.JpaRepository

interface AlertRepository : JpaRepository<AlertEntity, Long>
