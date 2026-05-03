package ru.leti.vkr.anomaly.rules

import org.springframework.stereotype.Component
import ru.leti.vkr.common.Alert
import ru.leti.vkr.common.SensorEvent
import java.time.Instant
import java.util.UUID

@Component
class RuleEngine(private val rules: List<AnomalyRule>) {

    fun evaluate(event: SensorEvent): List<Alert> =
        rules
            .filter { it.sensorType == event.sensorType && it.condition.matches(event.value, it.threshold) }
            .map { rule ->
                Alert(
                    alertId = UUID.randomUUID(),
                    roomId = event.roomId,
                    sensorType = event.sensorType,
                    ruleName = rule.name,
                    severity = rule.severity,
                    message = rule.description,
                    triggeringValue = event.value,
                    triggeredAt = Instant.now()
                )
            }
}
