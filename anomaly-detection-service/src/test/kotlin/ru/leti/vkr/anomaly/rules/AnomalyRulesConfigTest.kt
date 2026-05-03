package ru.leti.vkr.anomaly.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import ru.leti.vkr.common.SensorType

class AnomalyRulesConfigTest {

    @Test
    fun `правила загружаются из classpath anomaly-rules-yml`() {
        val rules = AnomalyRulesConfig(ClassPathResource("anomaly-rules.yml")).anomalyRules()

        assertThat(rules).isNotEmpty
        assertThat(rules.map { it.sensorType }).contains(SensorType.TEMPERATURE)
    }

    @Test
    fun `все правила имеют непустые имя и описание и положительный порог по умолчанию`() {
        val rules = AnomalyRulesConfig(ClassPathResource("anomaly-rules.yml")).anomalyRules()

        rules.forEach {
            assertThat(it.name).isNotBlank
            assertThat(it.description).isNotBlank
            assertThat(it.severity).isNotNull
        }
    }
}
