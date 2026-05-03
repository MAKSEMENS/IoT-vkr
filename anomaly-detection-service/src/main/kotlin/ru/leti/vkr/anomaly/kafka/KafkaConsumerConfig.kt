package ru.leti.vkr.anomaly.kafka

import org.apache.kafka.common.TopicPartition
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer
import org.springframework.kafka.listener.DefaultErrorHandler
import org.springframework.util.backoff.FixedBackOff

@Configuration
class KafkaConsumerConfig {

    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun deadLetterRecoverer(template: KafkaTemplate<String, Any>): DeadLetterPublishingRecoverer =
        DeadLetterPublishingRecoverer(template) { record, _ ->
            TopicPartition(record.topic() + ".DLT", record.partition())
        }

    @Bean
    fun errorHandler(recoverer: DeadLetterPublishingRecoverer): DefaultErrorHandler {
        val backOff = FixedBackOff(1_000L, 2L)
        val handler = DefaultErrorHandler(recoverer, backOff)
        handler.setRetryListeners({ record, ex, attempt ->
            log.warn(
                "Retry attempt {} for topic={} partition={} offset={}: {}",
                attempt, record.topic(), record.partition(), record.offset(), ex.message
            )
        })
        return handler
    }
}
