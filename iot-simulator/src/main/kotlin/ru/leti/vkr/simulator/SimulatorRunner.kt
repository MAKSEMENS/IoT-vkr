package ru.leti.vkr.simulator

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

@Component
class SimulatorRunner(
    private val generator: EventGenerator,
    private val sender: EventSender,
    private val props: SimulatorProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val perTick: Int =
        max(1, (props.eventsPerSecond.toLong() * props.tickMillis / 1000L).toInt())
    private val counter = AtomicLong()

    @Scheduled(fixedRateString = "\${simulator.tick-millis:100}")
    fun tick() {
        repeat(perTick) {
            sender.send(generator.next())
            counter.incrementAndGet()
        }
    }

    @Scheduled(fixedRate = 5_000)
    fun report() {
        val total = counter.get()
        if (total > 0) log.info("Sent {} events ({} eps target)", total, props.eventsPerSecond)
    }
}
