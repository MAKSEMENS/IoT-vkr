package ru.leti.vkr.anomaly.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import ru.leti.vkr.common.AlertSeverity
import ru.leti.vkr.common.SensorEvent
import ru.leti.vkr.common.SensorType
import java.time.Instant
import java.util.UUID

class RuleEngineTest {

    private val highTemperature = AnomalyRule(
        name = "high_temperature",
        sensorType = SensorType.TEMPERATURE,
        condition = RuleCondition.GREATER_THAN,
        threshold = 35.0,
        description = "Температура выше 35°C",
        severity = AlertSeverity.WARNING
    )

    private val criticalSmoke = AnomalyRule(
        name = "smoke_detected",
        sensorType = SensorType.SMOKE,
        condition = RuleCondition.GREATER_THAN,
        threshold = 0.5,
        description = "Задымление",
        severity = AlertSeverity.CRITICAL
    )

    private val lowHumidity = AnomalyRule(
        name = "low_humidity",
        sensorType = SensorType.HUMIDITY,
        condition = RuleCondition.LESS_THAN,
        threshold = 20.0,
        description = "Низкая влажность",
        severity = AlertSeverity.INFO
    )

    private val engine = RuleEngine(listOf(highTemperature, criticalSmoke, lowHumidity))

    @Test
    fun `срабатывает при превышении порога`() {
        val alerts = engine.evaluate(event(SensorType.TEMPERATURE, 36.5))
        assertThat(alerts).hasSize(1)
        assertThat(alerts[0].ruleName).isEqualTo("high_temperature")
        assertThat(alerts[0].severity).isEqualTo(AlertSeverity.WARNING)
        assertThat(alerts[0].triggeringValue).isEqualTo(36.5)
    }

    @Test
    fun `не срабатывает когда значение в норме`() {
        val alerts = engine.evaluate(event(SensorType.TEMPERATURE, 22.0))
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `не срабатывает на границе порога`() {
        assertThat(engine.evaluate(event(SensorType.TEMPERATURE, 35.0))).isEmpty()
        assertThat(engine.evaluate(event(SensorType.HUMIDITY, 20.0))).isEmpty()
    }

    @Test
    fun `фильтр по типу датчика - правило по другому типу не срабатывает`() {
        val alerts = engine.evaluate(event(SensorType.CO2, 99.0))
        assertThat(alerts).isEmpty()
    }

    @Test
    fun `LESS_THAN правило срабатывает при низком значении`() {
        val alerts = engine.evaluate(event(SensorType.HUMIDITY, 15.0))
        assertThat(alerts).hasSize(1)
        assertThat(alerts[0].ruleName).isEqualTo("low_humidity")
        assertThat(alerts[0].severity).isEqualTo(AlertSeverity.INFO)
    }

    @Test
    fun `severity берётся из правила`() {
        val warning = engine.evaluate(event(SensorType.TEMPERATURE, 40.0))
        val critical = engine.evaluate(event(SensorType.SMOKE, 0.9))
        val info = engine.evaluate(event(SensorType.HUMIDITY, 5.0))

        assertThat(warning[0].severity).isEqualTo(AlertSeverity.WARNING)
        assertThat(critical[0].severity).isEqualTo(AlertSeverity.CRITICAL)
        assertThat(info[0].severity).isEqualTo(AlertSeverity.INFO)
    }

    @Test
    fun `алерт содержит контекст события`() {
        val event = event(SensorType.TEMPERATURE, 50.0, room = "A101")
        val alert = engine.evaluate(event).single()

        assertThat(alert.roomId).isEqualTo("A101")
        assertThat(alert.sensorType).isEqualTo(SensorType.TEMPERATURE)
        assertThat(alert.message).isEqualTo("Температура выше 35°C")
        assertThat(alert.alertId).isNotNull
    }

    @Test
    fun `несколько правил по одному типу могут сработать одновременно`() {
        val twoTempRules = RuleEngine(
            listOf(
                highTemperature,
                AnomalyRule(
                    name = "very_high_temperature",
                    sensorType = SensorType.TEMPERATURE,
                    condition = RuleCondition.GREATER_THAN,
                    threshold = 45.0,
                    description = "Критически высокая температура",
                    severity = AlertSeverity.CRITICAL
                )
            )
        )
        val alerts = twoTempRules.evaluate(event(SensorType.TEMPERATURE, 50.0))
        assertThat(alerts).hasSize(2)
        assertThat(alerts.map { it.ruleName })
            .containsExactlyInAnyOrder("high_temperature", "very_high_temperature")
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
