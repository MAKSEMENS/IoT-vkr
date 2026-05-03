package ru.leti.vkr.aggregation.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import ru.leti.vkr.aggregation.domain.RoomStateEntity
import ru.leti.vkr.aggregation.domain.RoomStateRepository
import ru.leti.vkr.aggregation.domain.SensorEventEntity
import ru.leti.vkr.aggregation.domain.SensorEventRepository
import ru.leti.vkr.common.RoomState
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.common.SensorType

@Service
class RoomStateAggregator(
    private val sensorEvents: SensorEventRepository,
    private val roomStates: RoomStateRepository
) {
    @Transactional
    fun apply(event: SensorEvent): RoomState? {
        if (sensorEvents.existsByEventId(event.eventId)) return null

        sensorEvents.save(
            SensorEventEntity(
                eventId = event.eventId,
                roomId = event.roomId,
                sensorId = event.sensorId,
                sensorType = event.sensorType,
                value = event.value,
                recordedAt = event.timestamp
            )
        )

        val state = roomStates.findById(event.roomId).orElseGet {
            roomStates.save(RoomStateEntity(roomId = event.roomId, updatedAt = event.timestamp))
        }

        when (event.sensorType) {
            SensorType.TEMPERATURE -> state.temperature = event.value
            SensorType.HUMIDITY -> state.humidity = event.value
            SensorType.CO2 -> state.co2 = event.value
            SensorType.SMOKE -> state.smoke = event.value
            SensorType.MOTION -> state.motion = event.value
            SensorType.LIGHT -> state.light = event.value
        }
        state.updatedAt = event.timestamp

        val saved = roomStates.save(state)
        return saved.toDto()
    }
}

private fun RoomStateEntity.toDto(): RoomState {
    val readings = buildMap<SensorType, Double> {
        temperature?.let { put(SensorType.TEMPERATURE, it) }
        humidity?.let { put(SensorType.HUMIDITY, it) }
        co2?.let { put(SensorType.CO2, it) }
        smoke?.let { put(SensorType.SMOKE, it) }
        motion?.let { put(SensorType.MOTION, it) }
        light?.let { put(SensorType.LIGHT, it) }
    }
    return RoomState(
        roomId = roomId,
        readings = readings,
        updatedAt = updatedAt,
        version = version
    )
}
