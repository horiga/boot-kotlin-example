@file:Suppress("unused")

package org.horiga.study.web

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.TaskDecorator
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter
import java.util.UUID
import java.util.concurrent.Callable
import javax.servlet.FilterChain
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

@SpringBootApplication
class BootKotlinExampleApplication

const val TRANSACTION_ID: String = "txid"
fun HttpServletRequest.txid() = this.getAttribute(TRANSACTION_ID) as String

fun main(args: Array<String>) {
    // runApplication<BootKotlinExampleApplication>(*args)
    SpringApplication.run(BootKotlinExampleApplication::class.java, *args)
}

object Log {
    private val log = LoggerFactory.getLogger(Log::class.java)!!

    fun start(name: String, txid: String) {
        log.info("[$txid][START] $name")
    }

    fun end(name: String, txid: String) {
        log.info("[$txid][ END ] $name")
    }
}

class BootKotlinExampleApplicationFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val txid = UUID.randomUUID().toString()
        try {
            Log.start("oncePerRequestFilter", txid)
            // serve request transaction ID
            request.setAttribute(TRANSACTION_ID, txid)
            filterChain.doFilter(request, response)
        } finally {
            Log.end("oncePerRequestFilter", txid)
        }
    }
}

class BootKotlinExampleApplicationHandlerInterceptor : HandlerInterceptorAdapter() {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        try {
            Log.start("handlerInterceptor#preHandle", request.txid())
            return super.preHandle(request, response, handler)
        } finally {
            Log.end("handlerInterceptor#preHandle", request.txid())
        }
    }

    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?
    ) {
        try {
            Log.start("handlerInterceptor#postHandle", request.txid())
            super.postHandle(request, response, handler, modelAndView)
        } finally {
            Log.end("handlerInterceptor#postHandle", request.txid())
        }
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?
    ) {
        try {
            Log.start("handlerInterceptor#afterCompletion", request.txid())
            super.afterCompletion(request, response, handler, ex)
        } finally {
            Log.end("handlerInterceptor#afterCompletion", request.txid())
        }
    }

    override fun afterConcurrentHandlingStarted(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ) {
        try {
            Log.start("handlerInterceptor#afterConcurrentHandlingStarted", request.txid())
            super.afterConcurrentHandlingStarted(request, response, handler)
        } finally {
            Log.end("handlerInterceptor#afterConcurrentHandlingStarted", request.txid())
        }
    }
}

class BootKotlinExampleApplicationHandlerMethodArgumentResolver : HandlerMethodArgumentResolver {

    override fun supportsParameter(parameter: MethodParameter): Boolean =
        Message::class.java.isAssignableFrom(parameter.parameterType)

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        val txid = webRequest.getNativeRequest(HttpServletRequest::class.java)!!.txid()
        try {
            Log.start("methodArgumentResolver", txid)
            return Message(txid)
        } finally {
            Log.end("methodArgumentResolver", txid)
        }
    }
}

@ConfigurationProperties(prefix = "app.webmvc")
data class BootKotlinExampleApplicationProperties(
    val threadNamePrefix: String = "async-worker-",
    val corePoolSize: Int = 0,
    val maxPoolSize: Int = 200,
    val waitForTasksToCompleteOnShutdown: Boolean = true,
    val queueCapacity: Int = 10,
    val awaitTerminationSeconds: Int = 30
)

@Configuration
class BootKotlinExampleApplicationConfig {
    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper().registerModule(JavaTimeModule())
        .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, true)
        .configure(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS, false)
        .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}

@Configuration
@EnableConfigurationProperties(BootKotlinExampleApplicationProperties::class)
class BootKotlinExampleApplicationWebMvcConfigurer(
    val properties: BootKotlinExampleApplicationProperties
) : WebMvcConfigurer {

    class MdcTaskDecorator : TaskDecorator {
        override fun decorate(runnable: Runnable): Runnable =
            (RequestContextHolder.currentRequestAttributes().getAttribute(
                TRANSACTION_ID,
                RequestAttributes.SCOPE_REQUEST
            ) as String).let {
                try {
                    Log.start("taskDecorator(outer)", it)
                    val context = MDC.getCopyOfContextMap() ?: emptyMap()
                    // Dispatch IO thread to asyncTaskExecutor thread
                    Runnable {
                        try {
                            Log.start("taskDecorator(inner)", it)
                            MDC.setContextMap(context)
                            runnable.run()
                        } finally {
                            MDC.clear()
                            Log.end("taskDecorator(inner)", it)
                        }
                    }
                } finally {
                    Log.end("taskDecorator(outer)", it)
                }
            }
    }


    @Bean
    fun bootKotlinExampleApplicationFilterRegistrationBean() =
        FilterRegistrationBean<BootKotlinExampleApplicationFilter>().apply {
            filter = BootKotlinExampleApplicationFilter()
            addUrlPatterns("*")
        }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(BootKotlinExampleApplicationHandlerInterceptor())
    }

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver>) {
        resolvers.addAll(listOf(BootKotlinExampleApplicationHandlerMethodArgumentResolver()))
    }

    override fun configureAsyncSupport(configurer: AsyncSupportConfigurer) {
        configurer.setTaskExecutor(asyncTaskExecutor())
    }

    @Bean
    fun asyncTaskExecutor(): AsyncTaskExecutor =
        ThreadPoolTaskExecutor().apply {
            this.corePoolSize = when {
                properties.corePoolSize != 0 -> properties.corePoolSize
                Runtime.getRuntime().availableProcessors() == 1 -> 1
                else -> Runtime.getRuntime().availableProcessors() / 2
            }
            this.maxPoolSize = properties.maxPoolSize
            setQueueCapacity(properties.queueCapacity)
            setThreadNamePrefix(properties.threadNamePrefix)
            setWaitForTasksToCompleteOnShutdown(properties.waitForTasksToCompleteOnShutdown)
            setAllowCoreThreadTimeOut(true)
            setAwaitTerminationSeconds(properties.awaitTerminationSeconds)
            setTaskDecorator(MdcTaskDecorator())
            initialize()
        }

    // kotlin.coroutine
    @Bean
    fun mvcDispatcher(asyncTaskExecutor: AsyncTaskExecutor) = asyncTaskExecutor.asCoroutineDispatcher()
}

data class Message(
    val txid: String = ""
)

data class ReplyMessage(
    val id: String,
    val name: String
)

@RestController
@RequestMapping("/coroutine")
class CoroutineDispatcherController(
    private val mvcDispatcher: CoroutineDispatcher
) {
    @GetMapping
    fun get(
        @RequestParam(value = "name", required = false, defaultValue = "") name: String,
        message: Message
    ) =
        GlobalScope.future(mvcDispatcher) {
            try {
                Log.start("Controller", message.txid)
                ReplyMessage(message.txid, name)
            } finally {
                Log.end("Controller", message.txid)
            }
        }
}

@RestController()
@RequestMapping("/callable")
class CallableController {

    @GetMapping
    fun get(@RequestParam(value = "name", required = false, defaultValue = "") name: String,
            message: Message) = Callable<ReplyMessage> {
        try {
            Log.start("Controller", message.txid)
            ReplyMessage(message.txid, name)
        } finally {
            Log.end("Controller", message.txid)
        }
    }
}
