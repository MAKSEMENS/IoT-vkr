package ru.leti.vkr.aggregation

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.leti.vkr.aggregation.domain.RoomStateRepository
import ru.leti.vkr.aggregation.domain.SensorEventRepository
import ru.leti.vkr.common.KafkaTopics
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.common.SensorType
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@Testcontainers
class AggregationIntegrationTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    lateinit var roomStates: RoomStateRepository

    @Autowired
    lateinit var sensorEvents: SensorEventRepository

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
    fun `событие из Kafka обновляет room_state и сохраняется в sensor_events`() {
        val roomId = "ROOM-${UUID.randomUUID()}"
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = roomId,
            sensorId = "$roomId-temp",
            sensorType = SensorType.TEMPERATURE,
            value = 23.5,
            timestamp = Instant.now()
        )

        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, event)

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val state = roomStates.findById(roomId).orElse(null)
            assertThat(state).isNotNull
            assertThat(state.temperature).isEqualTo(23.5)
            assertThat(sensorEvents.existsByEventId(event.eventId)).isTrue
        }
    }

    @Test
    fun `повторное событие с тем же event_id не создаёт дубль и не инкрементирует version повторно`() {
        val roomId = "ROOM-${UUID.randomUUID()}"
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = roomId,
            sensorId = "$roomId-co2",
            sensorType = SensorType.CO2,
            value = 800.0,
            timestamp = Instant.now()
        )

        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, event)
        await().atMost(Duration.ofSeconds(30)).until { roomStates.existsById(roomId) }
        val firstVersion = roomStates.findById(roomId).get().version

        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, event)
        Thread.sleep(2000)

        val finalState = roomStates.findById(roomId).get()
        assertThat(finalState.version).isEqualTo(firstVersion)
        assertThat(sensorEvents.findAll().count { it.eventId == event.eventId }).isEqualTo(1)
    }

    @Test
    fun `последовательность событий обновляет несколько полей одной комнаты`() {
        val roomId = "ROOM-${UUID.randomUUID()}"
        val ts = Instant.now()
        val temperature = SensorEvent(UUID.randomUUID(), roomId, "$roomId-t", SensorType.TEMPERATURE, 22.0, ts)
        val humidity = SensorEvent(UUID.randomUUID(), roomId, "$roomId-h", SensorType.HUMIDITY, 55.0, ts)
        val co2 = SensorEvent(UUID.randomUUID(), roomId, "$roomId-c", SensorType.CO2, 750.0, ts)

        listOf(temperature, humidity, co2).forEach {
            kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, it)
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val state = roomStates.findById(roomId).orElse(null)
            assertThat(state).isNotNull
            assertThat(state.temperature).isEqualTo(22.0)
            assertThat(state.humidity).isEqualTo(55.0)
            assertThat(state.co2).isEqualTo(750.0)
        }
    }
}
