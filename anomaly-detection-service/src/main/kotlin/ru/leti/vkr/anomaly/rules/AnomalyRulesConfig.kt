package ru.leti.vkr.anomaly.rules

import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.Resource

@Configuration
class AnomalyRulesConfig(
    @Value("\${anomaly.rules-config}") private val rulesResource: Resource
) {
    @Bean
    fun anomalyRules(): List<AnomalyRule> {
        val mapper = YAMLMapper.builder()
            .addModule(kotlinModule())
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
            .build()
        rulesResource.inputStream.use { input ->
            return mapper.readValue(input, AnomalyRulesFile::class.java).rules
        }
    }
}
