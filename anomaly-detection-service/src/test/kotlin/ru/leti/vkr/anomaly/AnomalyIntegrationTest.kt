package ru.leti.vkr.anomaly

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.leti.vkr.anomaly.domain.AlertRepository
import ru.leti.vkr.common.KafkaTopics
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.common.SensorType
import java.time.Duration
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class AnomalyIntegrationTest {

    @Autowired
    lateinit var kafkaTemplate: KafkaTemplate<String, Any>

    @Autowired
    lateinit var alerts: AlertRepository

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
    fun `событие с превышением порога создаёт alert`() {
        val roomId = "ROOM-${UUID.randomUUID()}"
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = roomId,
            sensorId = "$roomId-temp",
            sensorType = SensorType.TEMPERATURE,
            value = 50.0,
            timestamp = Instant.now()
        )

        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, event)

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val matching = alerts.findAll().filter { it.roomId == roomId }
            assertThat(matching).isNotEmpty
            val alert = matching.first()
            assertThat(alert.ruleName).isEqualTo("high_temperature")
            assertThat(alert.triggeringValue).isEqualTo(50.0)
            assertThat(alert.sensorType).isEqualTo(SensorType.TEMPERATURE)
        }
    }

    @Test
    fun `событие в норме не создаёт alert`() {
        val roomId = "ROOM-NORMAL-${UUID.randomUUID()}"
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = roomId,
            sensorId = "$roomId-temp",
            sensorType = SensorType.TEMPERATURE,
            value = 22.5,
            timestamp = Instant.now()
        )

        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, event)
        Thread.sleep(3000)

        assertThat(alerts.findAll().filter { it.roomId == roomId }).isEmpty()
    }

    @Test
    fun `задымление выше порога создаёт CRITICAL alert`() {
        val roomId = "ROOM-SMOKE-${UUID.randomUUID()}"
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = roomId,
            sensorId = "$roomId-smoke",
            sensorType = SensorType.SMOKE,
            value = 0.9,
            timestamp = Instant.now()
        )

        kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, roomId, event)

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val matching = alerts.findAll().filter { it.roomId == roomId }
            assertThat(matching).isNotEmpty
            assertThat(matching.first().ruleName).isEqualTo("smoke_detected")
        }
    }
}
