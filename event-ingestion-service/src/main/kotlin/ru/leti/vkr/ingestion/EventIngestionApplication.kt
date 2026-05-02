package ru.leti.vkr.ingestion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EventIngestionApplication

fun main(args: Array<String>) {
    runApplication<EventIngestionApplication>(*args)
}
