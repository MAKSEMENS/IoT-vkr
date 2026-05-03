package ru.leti.vkr.ingestion.api

import jakarta.validation.Valid
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.ingestion.kafka.SensorEventProducer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@RestController
@RequestMapping("/events")
class SensorEventController(
    private val producer: SensorEventProducer
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val counter = AtomicLong()

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun ingest(@Valid @RequestBody request: SensorEventRequest): IngestResponse {
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = request.roomId,
            sensorId = request.sensorId,
            sensorType = request.sensorType,
            value = request.value,
            timestamp = request.timestamp ?: Instant.now()
        )
        producer.publish(event)
        val n = counter.incrementAndGet()
        if (n % 100L == 0L) log.info("Ingested {} events", n)
        return IngestResponse(event.eventId)
    }
}

data class IngestResponse(val eventId: UUID)
