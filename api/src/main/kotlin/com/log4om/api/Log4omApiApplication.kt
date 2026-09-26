package com.log4om.api

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class Log4omApiApplication

fun main(args: Array<String>) {
    runApplication<Log4omApiApplication>(*args)
}
