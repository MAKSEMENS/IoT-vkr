package ru.leti.vkr.anomaly

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.leti.vkr.anomaly.rules.RuleEngine
import ru.leti.vkr.common.KafkaTopics
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.common.SensorType
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class DlqIntegrationTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @MockBean
    lateinit var ruleEngine: RuleEngine

    companion object {
        @Container
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        @Container
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    @Test
    fun `падающее событие после 3 попыток отправляется в sensor-raw-events DLT`() {
        val calls = AtomicInteger()
        whenever(ruleEngine.evaluate(any())).doAnswer {
            calls.incrementAndGet()
            throw RuntimeException("simulated rule evaluation failure")
        }

        val roomId = "ROOM-${UUID.randomUUID()}"
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = roomId,
            sensorId = "$roomId-temp",
            sensorType = SensorType.TEMPERATURE,
            value = 25.0,
            timestamp = Instant.now()
        )

        val dltConsumer = newConsumer("dlt-test-${UUID.randomUUID()}")
        dltConsumer.subscribe(listOf("${KafkaTopics.SENSOR_RAW_EVENTS}.DLT"))

        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, event)

        await().atMost(Duration.ofSeconds(45)).untilAsserted {
            assertThat(calls.get())
                .`as`("evaluate должен быть вызван ровно 3 раза (initial + 2 retry)")
                .isEqualTo(3)
        }

        val dltRecord = await().atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofMillis(500))
            .until({ dltConsumer.poll(Duration.ofMillis(500)).firstOrNull() }, { it != null })!!

        val mapper = ObjectMapper().findAndRegisterModules()
        val payload = mapper.readTree(dltRecord.value())
        assertThat(payload["eventId"].asText()).isEqualTo(event.eventId.toString())
        assertThat(payload["roomId"].asText()).isEqualTo(roomId)

        dltConsumer.close()
    }

    private fun newConsumer(groupId: String): KafkaConsumer<String, String> {
        val props = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to kafka.bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java.name
        )
        return KafkaConsumer(props)
    }
}
