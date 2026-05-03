package ru.leti.vkr.aggregation.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import ru.leti.vkr.aggregation.domain.RoomStateEntity
import ru.leti.vkr.aggregation.domain.RoomStateRepository
import ru.leti.vkr.aggregation.domain.SensorEventEntity
import ru.leti.vkr.aggregation.domain.SensorEventRepository
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.common.SensorType
import java.time.Instant
import java.util.Optional
import java.util.UUID

class RoomStateAggregatorTest {

    private lateinit var sensorEvents: SensorEventRepository
    private lateinit var roomStates: RoomStateRepository
    private lateinit var aggregator: RoomStateAggregator

    @BeforeEach
    fun setUp() {
        sensorEvents = mock(SensorEventRepository::class.java)
        roomStates = mock(RoomStateRepository::class.java)
        aggregator = RoomStateAggregator(sensorEvents, roomStates)
    }

    @Test
    fun `первое событие создаёт состояние комнаты и записывает событие`() {
        val event = event(SensorType.TEMPERATURE, 22.5, room = "A1")
        `when`(sensorEvents.existsByEventId(event.eventId)).thenReturn(false)
        `when`(roomStates.findById("A1")).thenReturn(Optional.empty())
        `when`(roomStates.save(any(RoomStateEntity::class.java))).thenAnswer { it.arguments[0] }
        `when`(sensorEvents.save(any(SensorEventEntity::class.java))).thenAnswer { it.arguments[0] }

        val state = aggregator.apply(event)

        assertThat(state).isNotNull
        assertThat(state!!.roomId).isEqualTo("A1")
        assertThat(state.readings[SensorType.TEMPERATURE]).isEqualTo(22.5)
        verify(sensorEvents, times(1)).save(any(SensorEventEntity::class.java))
    }

    @Test
    fun `повторное событие с тем же event_id игнорируется (идемпотентность)`() {
        val event = event(SensorType.TEMPERATURE, 30.0, room = "A1")
        `when`(sensorEvents.existsByEventId(event.eventId)).thenReturn(true)

        val result = aggregator.apply(event)

        assertThat(result).isNull()
        verify(sensorEvents, never()).save(any(SensorEventEntity::class.java))
        verify(roomStates, never()).save(any(RoomStateEntity::class.java))
    }

    @Test
    fun `каждый тип датчика обновляет своё поле`() {
        val event = event(SensorType.HUMIDITY, 45.0, room = "A1")
        val existing = RoomStateEntity(roomId = "A1")
        `when`(sensorEvents.existsByEventId(event.eventId)).thenReturn(false)
        `when`(roomStates.findById("A1")).thenReturn(Optional.of(existing))
        `when`(roomStates.save(any(RoomStateEntity::class.java))).thenAnswer { it.arguments[0] }
        `when`(sensorEvents.save(any(SensorEventEntity::class.java))).thenAnswer { it.arguments[0] }

        aggregator.apply(event)

        val captor = ArgumentCaptor.forClass(RoomStateEntity::class.java)
        verify(roomStates).save(captor.capture())
        val saved = captor.value
        assertThat(saved.humidity).isEqualTo(45.0)
        assertThat(saved.temperature).isNull()
        assertThat(saved.co2).isNull()
    }

    @Test
    fun `маппинг типа датчика на поле RoomStateEntity покрывает все типы`() {
        val cases = listOf(
            SensorType.TEMPERATURE to { e: RoomStateEntity -> e.temperature },
            SensorType.HUMIDITY to { e: RoomStateEntity -> e.humidity },
            SensorType.CO2 to { e: RoomStateEntity -> e.co2 },
            SensorType.SMOKE to { e: RoomStateEntity -> e.smoke },
            SensorType.MOTION to { e: RoomStateEntity -> e.motion },
            SensorType.LIGHT to { e: RoomStateEntity -> e.light }
        )
        for ((type, accessor) in cases) {
            val sensorEvents = mock(SensorEventRepository::class.java)
            val roomStates = mock(RoomStateRepository::class.java)
            val agg = RoomStateAggregator(sensorEvents, roomStates)
            val event = event(type, 1.0)
            `when`(sensorEvents.existsByEventId(event.eventId)).thenReturn(false)
            `when`(roomStates.findById(event.roomId)).thenReturn(Optional.empty())
            `when`(roomStates.save(any(RoomStateEntity::class.java))).thenAnswer { it.arguments[0] }
            `when`(sensorEvents.save(any(SensorEventEntity::class.java))).thenAnswer { it.arguments[0] }

            agg.apply(event)

            val captor = ArgumentCaptor.forClass(RoomStateEntity::class.java)
            verify(roomStates, times(2)).save(captor.capture())
            val saved = captor.allValues.last()
            assertThat(accessor(saved)).`as`("поле для типа $type").isEqualTo(1.0)
        }
    }

    @Test
    fun `updatedAt берётся из timestamp события`() {
        val ts = Instant.parse("2026-05-03T10:00:00Z")
        val event = SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = "A1",
            sensorId = "A1-temp",
            sensorType = SensorType.TEMPERATURE,
            value = 20.0,
            timestamp = ts
        )
        `when`(sensorEvents.existsByEventId(event.eventId)).thenReturn(false)
        `when`(roomStates.findById("A1")).thenReturn(Optional.empty())
        `when`(roomStates.save(any(RoomStateEntity::class.java))).thenAnswer { it.arguments[0] }
        `when`(sensorEvents.save(any(SensorEventEntity::class.java))).thenAnswer { it.arguments[0] }

        val state = aggregator.apply(event)

        assertThat(state!!.updatedAt).isEqualTo(ts)
    }

    private fun event(type: SensorType, value: Double, room: String = "R-1") =
        SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = room,
            sensorId = "$room-${type.name.lowercase()}",
            sensorType = type,
            value = value,
            timestamp = Instant.parse("2026-05-03T10:00:00Z")
        )
}
