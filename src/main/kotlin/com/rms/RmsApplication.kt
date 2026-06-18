package com.rms

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class RmsApplication

fun main(args: Array<String>) {
    runApplication<RmsApplication>(*args)
}
