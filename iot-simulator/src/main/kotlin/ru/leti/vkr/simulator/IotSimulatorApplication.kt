package ru.leti.vkr.simulator

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class IotSimulatorApplication

fun main(args: Array<String>) {
    runApplication<IotSimulatorApplication>(*args)
}
