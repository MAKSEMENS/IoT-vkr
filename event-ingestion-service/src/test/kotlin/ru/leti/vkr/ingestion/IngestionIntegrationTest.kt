package ru.leti.vkr.ingestion

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.StringDeserializer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import ru.leti.vkr.common.KafkaTopics
import java.time.Duration
import java.util.Properties
import java.util.UUID

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IngestionIntegrationTest {

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var rest: TestRestTemplate

    companion object {
        @Container
        @JvmStatic
        val kafka: KafkaContainer = KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
        }
    }

    @Test
    fun `POST events публикует SensorEvent в sensor-raw-events`() {
        val roomId = "ROOM-${UUID.randomUUID()}"
        val payload = mapOf<String, Any>(
            "roomId" to roomId,
            "sensorId" to "$roomId-temp",
            "sensorType" to "TEMPERATURE",
            "value" to 24.7
        )

        val response = rest.postForEntity("/events", payload, Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.ACCEPTED)
        assertThat(response.body!!.keys).contains("eventId")

        kafkaConsumer().use { consumer ->
            consumer.subscribe(listOf(KafkaTopics.SENSOR_RAW_EVENTS))
            await().atMost(Duration.ofSeconds(20)).untilAsserted {
                val records = consumer.poll(Duration.ofMillis(500))
                val matching = records.filter { it.key() == roomId }
                assertThat(matching).isNotEmpty
                val value = matching.first().value()
                assertThat(value).contains(roomId)
                assertThat(value).contains("TEMPERATURE")
                assertThat(value).contains("24.7")
            }
        }
    }

    @Test
    fun `невалидный запрос отдаёт 400`() {
        val invalid = mapOf<String, Any>(
            "roomId" to "",
            "sensorId" to "x",
            "sensorType" to "TEMPERATURE",
            "value" to 1.0
        )
        val response = rest.postForEntity("/events", invalid, Map::class.java)
        assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
    }

    private fun kafkaConsumer(): KafkaConsumer<String, String> {
        val props = Properties().apply {
            setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.bootstrapServers)
            setProperty(ConsumerConfig.GROUP_ID_CONFIG, "test-${UUID.randomUUID()}")
            setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
            setProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
        }
        return KafkaConsumer(props, StringDeserializer(), StringDeserializer())
    }
}
