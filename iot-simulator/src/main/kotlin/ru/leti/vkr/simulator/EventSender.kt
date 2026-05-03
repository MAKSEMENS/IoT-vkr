package ru.leti.vkr.simulator

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import ru.leti.vkr.common.SensorEvent

@Component
class EventSender(builder: WebClient.Builder, props: SimulatorProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val client: WebClient = builder.baseUrl(props.targetUrl).build()

    fun send(event: SensorEvent) {
        client.post()
            .bodyValue(event)
            .retrieve()
            .toBodilessEntity()
            .doOnError { log.warn("Failed to send event: {}", it.message) }
            .subscribe()
    }
}
