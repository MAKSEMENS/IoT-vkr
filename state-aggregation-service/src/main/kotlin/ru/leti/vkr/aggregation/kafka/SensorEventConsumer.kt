package ru.leti.vkr.aggregation.kafka

import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component
import ru.leti.vkr.aggregation.service.RoomStateAggregator
import ru.leti.vkr.common.KafkaTopics
import ru.leti.vkr.common.SensorEvent

@Component
class SensorEventConsumer(
    private val aggregator: RoomStateAggregator,
    private val producer: RoomStateProducer
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val counter = java.util.concurrent.atomic.AtomicLong()

    @KafkaListener(
        topics = [KafkaTopics.SENSOR_RAW_EVENTS],
        groupId = "\${spring.kafka.consumer.group-id}"
    )
    fun onEvent(event: SensorEvent) {
        val newState = aggregator.apply(event) ?: run {
            log.debug("Duplicate event {} skipped", event.eventId)
            return
        }
        producer.publish(newState)
        val n = counter.incrementAndGet()
        if (n % 100L == 0L) log.info("Aggregated {} events; last room={} version={}", n, newState.roomId, newState.version)
    }
}
