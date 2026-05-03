package ru.leti.vkr.aggregation.kafka

import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import ru.leti.vkr.common.KafkaTopics
import ru.leti.vkr.common.RoomState

@Component
class RoomStateProducer(
    private val kafkaTemplate: KafkaTemplate<String, Any>
) {
    fun publish(state: RoomState) {
        kafkaTemplate.send(KafkaTopics.ROOM_STATE_EVENTS, state.roomId, state)
    }
}
