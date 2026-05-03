package ru.leti.vkr.simulator

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.math.max

@Component
class SimulatorRunner(
    private val generator: EventGenerator,
    private val sender: EventSender,
    private val props: SimulatorProperties
) {
    private val perTick: Int =
        max(1, (props.eventsPerSecond.toLong() * props.tickMillis / 1000L).toInt())

    @Scheduled(fixedRateString = "\${simulator.tick-millis:100}")
    fun tick() {
        repeat(perTick) {
            sender.send(generator.next())
        }
    }
}
