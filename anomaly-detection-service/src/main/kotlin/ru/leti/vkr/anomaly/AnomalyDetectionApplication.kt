package ru.leti.vkr.anomaly

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class AnomalyDetectionApplication

fun main(args: Array<String>) {
    runApplication<AnomalyDetectionApplication>(*args)
}
