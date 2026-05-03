package ru.leti.vkr.aggregation.admin

import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReplayIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var rest: TestRestTemplate

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
    fun `replay стирает room_states и пересчитывает их из sensor_events`() {
        val roomId = "ROOM-${UUID.randomUUID()}"
        val events = listOf(
            event(roomId, SensorType.TEMPERATURE, 22.0),
            event(roomId, SensorType.HUMIDITY, 50.0),
            event(roomId, SensorType.CO2, 700.0)
        )
        events.forEach { kafkaTemplate.send(KafkaTopics.SENSOR_RAW_EVENTS, it.roomId, it) }

        await().atMost(Duration.ofSeconds(30)).untilAsserted {
            val state = roomStates.findById(roomId).orElse(null)
            assertThat(state).isNotNull
            assertThat(state.temperature).isEqualTo(22.0)
            assertThat(state.humidity).isEqualTo(50.0)
            assertThat(state.co2).isEqualTo(700.0)
        }
        val versionBefore = roomStates.findById(roomId).get().version
        val eventsBefore = sensorEvents.count()

        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = rest.withBasicAuth("admin", "admin")
            .exchange("/admin/replay", HttpMethod.POST, HttpEntity<String>("", headers), Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body!!["success"]).isEqualTo(true)

        await().atMost(Duration.ofSeconds(60)).untilAsserted {
            val state = roomStates.findById(roomId).orElse(null)
            assertThat(state).isNotNull
            assertThat(state.temperature).isEqualTo(22.0)
            assertThat(state.humidity).isEqualTo(50.0)
            assertThat(state.co2).isEqualTo(700.0)
        }

        assertThat(sensorEvents.count())
            .`as`("sensor_events не должны были удалиться")
            .isEqualTo(eventsBefore)
        assertThat(roomStates.findById(roomId).get().version)
            .`as`("после replay version должен начаться заново")
            .isLessThanOrEqualTo(versionBefore)
    }

    @Test
    fun `с неверными креденшелами admin replay отдаёт 401`() {
        val headers = HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }
        val response = rest.withBasicAuth("nobody", "nope")
            .exchange("/admin/replay", HttpMethod.POST, HttpEntity<String>("", headers), String::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.UNAUTHORIZED)
    }

    private fun event(roomId: String, type: SensorType, value: Double) = SensorEvent(
        eventId = UUID.randomUUID(),
        roomId = roomId,
        sensorId = "$roomId-${type.name.lowercase()}",
        sensorType = type,
        value = value,
        timestamp = Instant.now()
    )
}
