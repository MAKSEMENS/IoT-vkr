package ru.leti.vkr.anomaly.rules

import com.fasterxml.jackson.annotation.JsonProperty
import ru.leti.vkr.common.AlertSeverity
import ru.leti.vkr.common.SensorType

data class AnomalyRule(
    val name: String,
    @JsonProperty("sensor_type") val sensorType: SensorType,
    val condition: RuleCondition,
    val threshold: Double,
    val description: String,
    val severity: AlertSeverity = AlertSeverity.WARNING
)

data class AnomalyRulesFile(val rules: List<AnomalyRule>)
