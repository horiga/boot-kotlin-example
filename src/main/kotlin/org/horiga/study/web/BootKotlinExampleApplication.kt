package org.horiga.study.web

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication

@SpringBootApplication
class BootKotlinExampleApplication

fun main(args: Array<String>) {
    // runApplication<BootKotlinExampleApplication>(*args)
    SpringApplication.run(BootKotlinExampleApplication::class.java, *args)
}


