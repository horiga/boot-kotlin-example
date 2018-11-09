package org.horiga.study.webflux

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import reactor.core.publisher.Mono

@SpringBootApplication
class BootKotlinExampleApplication

fun main(args: Array<String>) {
    runApplication<BootKotlinExampleApplication>(*args)
}

object Log {

    val log = LoggerFactory.getLogger(Log::class.java)!!

    fun start(name: String) {
        log.info("[START] $name")
    }

    fun end(name: String) {
        log.info("[ END ] $name")
    }
}

@ConfigurationProperties(prefix = "")
data class BootKotlinExampleApplicationProperties(
    val hoge: String = ""
)

@Configuration
@EnableConfigurationProperties(BootKotlinExampleApplicationProperties::class)
class WebfluxRouters(val properties: BootKotlinExampleApplicationProperties) {

    @Bean
    fun routers(echoHandler: EchoHandler) = router {
        GET("/echo", echoHandler::get)
    }.filter(EchoFilter())
}

// filters
class EchoFilter : HandlerFilterFunction<ServerResponse, ServerResponse> {
    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> =
        try {
            Log.start("echoFilter")
            next.handle(request)
        } finally {
            Log.end("echoFilter")
        }
}

fun json(body: Any, status: HttpStatus = HttpStatus.OK) = ServerResponse.status(status)
    .contentType(MediaType.APPLICATION_JSON_UTF8).body(BodyInserters.fromObject(body))

// -- handler
@Component
class EchoHandler {

    data class ReplyMessage(val message: String = "")

    fun get(request: ServerRequest): Mono<ServerResponse> = try {
        Log.start("echoHandler")
        json(ReplyMessage("Hello, " + request.queryParam("echo").orElse("Webflux")))
    } finally {
        Log.end("echoHandler")
    }
}