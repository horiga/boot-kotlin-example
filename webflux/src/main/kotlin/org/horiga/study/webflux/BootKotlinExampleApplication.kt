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
import reactor.core.publisher.doOnError

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

    companion object {
        val log = LoggerFactory.getLogger(EchoFilter::class.java)!!
    }

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> {
        Log.start("echoFilter")
        return next.handle(request)
            .doOnEach {
                log.info(">> filter#doOnEach, signal.name=${it.type.name}")
            }.doOnSuccessOrError { _, _ ->
                log.info(">> filter#doOnSuccessOrError")
            }.doOnSuccess {
                log.info(">> filter#doOnSuccess, status=${it.statusCode()}")
            }.doOnError {// FIXME can't handle exception....
                log.warn(">> filter#doOnError, cause=${it?.message}")
            }.doOnError(UnsupportedOperationException::class.java) { // FIXME can't handle exception....
                log.warn(">> filter#doOnError with class), cause=${it?.message}")
            }.doAfterSuccessOrError { _, _ ->
                log.info(">> filter#doAfterSuccessOrError")
            }.doAfterTerminate {
                log.info(">> filter#doAfterTerminate")
            }.doFinally {
                Log.end("echoFilter")
            }
    }
}

fun json(body: Any, status: HttpStatus = HttpStatus.OK) = ServerResponse.status(status)
    .contentType(MediaType.APPLICATION_JSON_UTF8).body(BodyInserters.fromObject(body))

// -- handler
@Component
class EchoHandler {

    companion object {
        val log = LoggerFactory.getLogger(EchoHandler::class.java)!!
    }

    data class ReplyMessage(val message: String = "")

    data class ErrorMessage(val status: Int, val message: String = "error")

    @Throws(Exception::class)
    fun get(request: ServerRequest): Mono<ServerResponse> {
        Log.start("echoHandler")
        val q = request.queryParam("echo").orElse("Webflux")
        return when (q) {
            "error" -> throw UnsupportedOperationException("error!")
            "400" -> json(ErrorMessage(400, "bad request"), HttpStatus.BAD_REQUEST)
            else -> json(ReplyMessage("Hello, $q"))
        }
            .doOnEach { log.info("handler#doOnEach, signal.name=${it.type.name}") }
            .doOnSuccessOrError { _, _ -> log.info("handler#doOnSuccessOrError") }
            .doOnSuccess { log.info("handler#doOnSuccess, status.code=${it.statusCode()}") }
            .doOnError(UnsupportedOperationException::class.java) {
                log.warn("handler#doOnError('UnsupportedOperationException')")
            } // FIXME can't handle exception....
            .doOnError { ex -> log.warn("handler#doOnError, error.message=${ex.message}") } // FIXME can't handle exception....
            .doAfterSuccessOrError { _, _ -> log.info(">> handler#doAfterSuccessOrError") }
            .doAfterTerminate { log.info("handler#doAfterTerminate") }
            .doOnSuccessOrError { _, _ -> log.warn("handler#doOnSuccessOrError") }
            .doFinally { Log.end("echoHandler") }
    }
}