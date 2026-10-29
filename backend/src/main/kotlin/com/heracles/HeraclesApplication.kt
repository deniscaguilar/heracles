package com.heracles

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HeraclesApplication

fun main(args: Array<String>) {
    runApplication<HeraclesApplication>(*args)
}
