package com.campusute.backend

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan

@SpringBootApplication
@ConfigurationPropertiesScan
class BackendApplication

fun main(args: Array<String>) {
    runApplication<BackendApplication>(*args)
}
