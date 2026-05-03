package ru.leti.vkr.anomaly.kafka

import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import ru.leti.vkr.anomaly.domain.AlertEntity
import ru.leti.vkr.anomaly.domain.AlertRepository
import ru.leti.vkr.anomaly.rules.RuleEngine
import ru.leti.vkr.common.Alert
import ru.leti.vkr.common.KafkaTopics
import ru.leti.vkr.common.SensorEvent

@Component
class SensorEventConsumer(
    private val ruleEngine: RuleEngine,
    private val alerts: AlertRepository,
    private val producer: AlertProducer
) {
    @KafkaListener(
        topics = [KafkaTopics.SENSOR_RAW_EVENTS],
        groupId = "\${spring.kafka.consumer.group-id}"
    )
    @Transactional
    fun onEvent(event: SensorEvent) {
        ruleEngine.evaluate(event).forEach { alert ->
            alerts.save(alert.toEntity())
            producer.publish(alert)
        }
    }
}

private fun Alert.toEntity() = AlertEntity(
    alertId = alertId,
    roomId = roomId,
    sensorType = sensorType,
    ruleName = ruleName,
    severity = severity,
    message = message,
    triggeringValue = triggeringValue,
    triggeredAt = triggeredAt
)
