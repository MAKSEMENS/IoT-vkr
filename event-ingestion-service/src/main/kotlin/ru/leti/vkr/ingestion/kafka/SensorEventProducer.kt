package ru.leti.vkr.ingestion.kafka

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.stereotype.Component
import ru.leti.vkr.common.KafkaTopics
import ru.leti.vkr.common.SensorEvent
import java.util.concurrent.CompletableFuture

@Component
class SensorEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    fun publish(event: SensorEvent): CompletableFuture<SendResult<String, Any>> =
        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, event.roomId, event)
}
