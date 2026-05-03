package ru.leti.vkr.anomaly.rules

enum class RuleCondition {
    GREATER_THAN,
    LESS_THAN;

    fun matches(value: Double, threshold: Double): Boolean = when (this) {
        GREATER_THAN -> value > threshold
        LESS_THAN -> value < threshold
    }
}
