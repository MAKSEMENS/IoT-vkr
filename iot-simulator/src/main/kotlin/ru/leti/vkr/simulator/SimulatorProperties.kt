package ru.leti.vkr.simulator

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "simulator")
data class SimulatorProperties(
    val targetUrl: String,
    val eventsPerSecond: Int,
    val rooms: List<String>,
    val anomalyProbability: Double = 0.02,
    val tickMillis: Long = 100
)
