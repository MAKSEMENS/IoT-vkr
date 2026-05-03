package ru.leti.vkr.aggregation.admin

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.Timer
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import ru.leti.vkr.aggregation.domain.RoomStateEntity
import ru.leti.vkr.aggregation.domain.RoomStateRepository
import ru.leti.vkr.aggregation.domain.SensorEventEntity
import ru.leti.vkr.aggregation.domain.SensorEventRepository
import ru.leti.vkr.common.SensorType
import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@Service
class ReplayService(
    private val registry: KafkaListenerEndpointRegistry,
    private val roomStates: RoomStateRepository,
    private val sensorEvents: SensorEventRepository,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)
    private val gaugeFlag = AtomicInteger(0)

    private val invocations: Counter = Counter
        .builder("replay_invocations_total")
        .description("Сколько раз вызывался /admin/replay")
        .register(meterRegistry)

    private val processed: Counter = Counter
        .builder("replay_events_processed_total")
        .description("Сколько событий пересчитано в рамках replay с момента старта сервиса")
        .register(meterRegistry)

    private val timer: Timer = Timer
        .builder("replay_duration_seconds")
        .description("Длительность операции replay")
        .register(meterRegistry)

    init {
        meterRegistry.gauge("replay_in_progress", Tags.empty(), gaugeFlag) { it.get().toDouble() }
    }

    fun replay(): ReplayResult {
        if (!running.compareAndSet(false, true)) {
            return ReplayResult(success = false, message = "Replay уже выполняется", recomputedRooms = 0, processedEvents = 0)
        }
        gaugeFlag.set(1)
        invocations.increment()
        val startedNs = System.nanoTime()

        return try {
            log.info("Replay: начинаем")

            val containers = registry.allListenerContainers
            containers.forEach {
                log.info("Replay: останавливаем listener {}", it.listenerId)
                it.stop()
            }

            val (eventsCount, roomsCount) = recomputeFromEventLog()

            containers.forEach {
                log.info("Replay: запускаем listener {}", it.listenerId)
                it.start()
            }

            val tookMs = (System.nanoTime() - startedNs) / 1_000_000
            timer.record(Duration.ofMillis(tookMs))
            log.info("Replay: завершён за {} мс. Событий: {}, комнат: {}", tookMs, eventsCount, roomsCount)

            ReplayResult(
                success = true,
                message = "Replay завершён: пересчитано $eventsCount событий по $roomsCount комнатам",
                recomputedRooms = roomsCount,
                processedEvents = eventsCount
            )
        } catch (ex: Exception) {
            log.error("Replay упал: {}", ex.message, ex)
            ReplayResult(success = false, message = "Replay упал: ${ex.message}", recomputedRooms = 0, processedEvents = 0)
        } finally {
            running.set(false)
            gaugeFlag.set(0)
        }
    }

    private fun recomputeFromEventLog(): Pair<Long, Long> {
        return transactionTemplate.execute { _ ->
            entityManager.createNativeQuery("TRUNCATE TABLE room_states").executeUpdate()
            entityManager.flush()

            val states = HashMap<String, RoomStateEntity>()
            var count = 0L

            sensorEvents.streamAllOrderedByTime().use { stream ->
                stream.forEach { event ->
                    val state = states.getOrPut(event.roomId) { RoomStateEntity(roomId = event.roomId) }
                    applyToState(state, event)
                    state.updatedAt = event.recordedAt
                    count += 1
                    processed.increment()
                }
            }

            states.values.forEach { entityManager.persist(it) }
            entityManager.flush()

            count to states.size.toLong()
        }!!
    }

    private fun applyToState(state: RoomStateEntity, event: SensorEventEntity) {
        when (event.sensorType) {
            SensorType.TEMPERATURE -> state.temperature = event.value
            SensorType.HUMIDITY -> state.humidity = event.value
            SensorType.CO2 -> state.co2 = event.value
            SensorType.SMOKE -> state.smoke = event.value
            SensorType.MOTION -> state.motion = event.value
            SensorType.LIGHT -> state.light = event.value
        }
    }

    data class ReplayResult(
        val success: Boolean,
        val message: String,
        val recomputedRooms: Long,
        val processedEvents: Long
    )
}
