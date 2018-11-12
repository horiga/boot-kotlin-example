package org.horiga.study.webflux

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.HandlerFilterFunction
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.router
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono

@SpringBootApplication
@EnableWebFlux
class BootKotlinExampleApplication

fun main(args: Array<String>) {
    runApplication<BootKotlinExampleApplication>(*args)
}

object Log {

    private val log = LoggerFactory.getLogger(Log::class.java)!!

    fun start(name: String) = log.info("[START] $name")
    fun end(name: String) = log.info("[ END ] $name")
    fun event(name: String, event: String) = log.info("[EVENT] <$name> $event")
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
        accept(MediaType.APPLICATION_JSON_UTF8).nest {
            GET("/echo", echoHandler::get)
        }
    }.filter(EchoFilterFunction())
}

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
class EchoWebFilter : WebFilter {
    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        try {
            Log.start("webFilter")
            val s = "webFilter"
            return chain.filter(exchange)
                .doOnEach { Log.event(s, "doOnEach") }
                .doFinally { Log.event(s, "doOnFinally") }
                .doOnSuccess { Log.event(s, "doOnSuccess") }
                .doOnSuccessOrError { _, _ -> Log.event(s, "doOnSuccessOrError") }
                .doOnTerminate { Log.event(s, "doOnTerminate") }
                .doAfterSuccessOrError { _, _ -> Log.event(s, "doAfterSuccessOrError") }
                .doAfterTerminate { Log.event(s, "doAfterTerminate") }
        } finally {
            Log.end("webFilter")
        }
    }
}

// filters
class EchoFilterFunction : HandlerFilterFunction<ServerResponse, ServerResponse> {

    companion object {
        val log = LoggerFactory.getLogger(EchoFilterFunction::class.java)!!
    }

    override fun filter(request: ServerRequest, next: HandlerFunction<ServerResponse>): Mono<ServerResponse> =
        try {
            val s = "filterFunction"
            Log.start(s)
            next.handle(request)
                .doOnEach { Log.event(s, "doOnEach") }
                .doOnSuccessOrError { _, _ -> Log.event(s, "doOnSuccessOrError") }
                .doOnSuccess { Log.event(s, "doOnSuccess") }
                .doOnError { Log.event(s, "doOnError, cause=${it?.message}") }
                .doAfterSuccessOrError { _, _ -> Log.event(s, "doAfterSuccessOrError") }
                .doAfterTerminate { Log.event(s, "doAfterTerminate") }
                .doFinally { Log.event(s, "doOnFinally") }
        } finally {
            Log.end("filterFunction")
        }
}

fun json(body: Any, status: HttpStatus = HttpStatus.OK) = ServerResponse.status(status)
    .contentType(MediaType.APPLICATION_JSON_UTF8).body(BodyInserters.fromObject(body))

data class ReplyMessage(val message: String = "", val status: Int = 200)

// -- handler
@Component
class EchoHandler(private val echoService: EchoService) {

    companion object {
        val log = LoggerFactory.getLogger(EchoHandler::class.java)!!
        //val webClient: WebClient = WebClient.create()
    }

    @Throws(Exception::class)
    fun get(request: ServerRequest): Mono<ServerResponse> = try {
        val s = "handler"
        Log.start(s)
        val q = request.queryParam("echo").orElse("Webflux")
        Mono.create<ReplyMessage> {
            try {
                it.success(echoService.echo(q))
            } catch (e: Exception) {
                it.error(e)
            }
        }
            .doOnEach { Log.event(s, "doOnEach") }
            .doOnSuccessOrError { _, _ -> Log.event(s, "doOnSuccessOrError") }
            .doOnSuccess { Log.event(s, "doOnSuccess") }
            .doOnError { Log.event(s, "doOnError, cause=${it?.message}") }
            .doAfterSuccessOrError { _, _ -> Log.event(s, "doAfterSuccessOrError") }
            .doAfterTerminate { Log.event(s, "doAfterTerminate") }
            .doFinally { Log.event(s, "doOnFinally") }
            .onErrorReturn(
                IllegalArgumentException::class.java,
                ReplyMessage("IllegalArgumentException", 400)
            )
            .onErrorReturn(
                UnsupportedOperationException::class.java,
                ReplyMessage("UnsupportedOperationException", 500)
            )
            .flatMap {
                json(it, HttpStatus.valueOf(it.status))
            }
    } finally {
        Log.end("handler")
    }
}

// service

@Service
class EchoService {

    fun echo(message: String): ReplyMessage {
        Log.start("service")
        try {
            return when (message) {
                "error" -> throw UnsupportedOperationException("")
                "400" -> throw IllegalArgumentException("")
                else -> ReplyMessage(message)
            }
        } finally {
            Log.end("service")
        }
    }

}

