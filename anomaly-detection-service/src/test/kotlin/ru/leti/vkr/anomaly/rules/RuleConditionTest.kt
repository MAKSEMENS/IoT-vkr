package ru.leti.vkr.anomaly.rules

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RuleConditionTest {

    @Test
    fun `GREATER_THAN срабатывает только когда значение строго больше порога`() {
        assertThat(RuleCondition.GREATER_THAN.matches(35.1, 35.0)).isTrue
        assertThat(RuleCondition.GREATER_THAN.matches(35.0, 35.0)).isFalse
        assertThat(RuleCondition.GREATER_THAN.matches(34.9, 35.0)).isFalse
    }

    @Test
    fun `LESS_THAN срабатывает только когда значение строго меньше порога`() {
        assertThat(RuleCondition.LESS_THAN.matches(9.9, 10.0)).isTrue
        assertThat(RuleCondition.LESS_THAN.matches(10.0, 10.0)).isFalse
        assertThat(RuleCondition.LESS_THAN.matches(10.1, 10.0)).isFalse
    }
}