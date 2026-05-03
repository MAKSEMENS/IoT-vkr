package ru.leti.vkr.simulator

import org.springframework.stereotype.Component
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.common.SensorType
import java.time.Instant
import java.util.UUID
import kotlin.random.Random

@Component
class EventGenerator(private val props: SimulatorProperties) {

    fun next(): SensorEvent {
        val room = props.rooms.random()
        val type = SensorType.entries.random()
        val anomaly = Random.nextDouble() < props.anomalyProbability
        val value = sample(type, anomaly)
        return SensorEvent(
            eventId = UUID.randomUUID(),
            roomId = room,
            sensorId = "$room-${type.name.lowercase()}",
            sensorType = type,
            value = value,
            timestamp = Instant.now()
        )
    }

    private fun sample(type: SensorType, anomaly: Boolean): Double = when (type) {
        SensorType.TEMPERATURE -> if (anomaly) Random.nextDouble(36.0, 50.0) else Random.nextDouble(18.0, 26.0)
        SensorType.HUMIDITY -> if (anomaly) Random.nextDouble(91.0, 100.0) else Random.nextDouble(30.0, 65.0)
        SensorType.CO2 -> if (anomaly) Random.nextDouble(1501.0, 3000.0) else Random.nextDouble(400.0, 900.0)
        SensorType.SMOKE -> if (anomaly) Random.nextDouble(0.51, 1.0) else Random.nextDouble(0.0, 0.1)
        SensorType.MOTION -> if (Random.nextBoolean()) 1.0 else 0.0
        SensorType.LIGHT -> Random.nextDouble(0.0, 1000.0)
    }
}
