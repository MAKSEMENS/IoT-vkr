package ru.leti.vkr.anomaly.kafka

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import ru.leti.vkr.common.Alert
import ru.leti.vkr.common.KafkaTopics

@Component
class AlertProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    fun publish(alert: Alert) {
        kafkaTemplate.send(KafkaTopics.ALERT_EVENTS, alert.roomId, alert)
    }
}
