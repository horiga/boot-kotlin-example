package org.horiga.study.web.support.testcontainers

import org.junit.jupiter.api.extension.ExtendWith
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.annotation.AliasFor
import org.springframework.core.env.Environment
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.testcontainers.containers.DockerComposeContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.containers.wait.strategy.WaitStrategy
import java.io.File
import java.lang.annotation.Inherited
import java.nio.file.Paths
import kotlin.reflect.KClass

@ExtendWith(SpringExtension::class)
@WithDockerContainers
@MybatisTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
abstract class DockerComposeTestSupport {
    @Autowired
    lateinit var jdbcTemplate: JdbcTemplate
}

@Suppress("unused")
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
@Inherited
@EnableConfigurationProperties(CommonTestContainersProperties::class)
@ContextConfiguration
annotation class
WithDockerContainers(
    @get:AliasFor(annotation = ContextConfiguration::class, attribute = "initializers")
    @Suppress("unused")
    val initializers: Array<KClass<out ApplicationContextInitializer<*>>> =
        [CommonTestContainers.Initializer::class]
)

@ConfigurationProperties(prefix = "test.docker")
data class CommonTestContainersProperties(
    var dockerComposeFile: String = "../docker/docker-compose.test.yml",
    var mysql: MySQLProperties = MySQLProperties()
) {
    companion object {
        fun fromEnvironment(env: Environment) = CommonTestContainersProperties().apply {
            dockerComposeFile =
                env.getProperty("test.docker.docker-compose-file", "../docker/docker-compose.test.yml")
            mysql.name = env.getProperty("test.docker.mysql.name", "test")
            mysql.port = env.getProperty("test.docker.mysql.port", "3306").toInt()
            mysql.serviceName = env.getProperty("test.docker.mysql.serviceName", "mysql")
        }
    }

    data class MySQLProperties(
        var name: String = "test",
        var port: Int = 3306,
        var serviceName: String = "mysql"
    )
}

object CommonTestContainers {

    private val log = LoggerFactory.getLogger(CommonTestContainers::class.java)!!

    private val MYSQL_CONTAINERS_EXPOSE_WAIT_STRATEGY: WaitStrategy =
        Wait.forLogMessage(".*ready for connections.*\\s", 2)

    private lateinit var properties: CommonTestContainersProperties

    private val container: KDockerComposeContainer by lazy {
        KDockerComposeContainer(Paths.get(properties.dockerComposeFile).toAbsolutePath().normalize().toFile())
            .withExposedService(
                properties.mysql.serviceName,
                properties.mysql.port,
                MYSQL_CONTAINERS_EXPOSE_WAIT_STRATEGY
            )
            .withExposedService(
                "redis-cluster",
                7000,
                // it is better to use https://github.com/testcontainers/testcontainers-spring-boot/blob/develop/embedded-redis/src/main/java/com/playtika/test/redis/wait/DefaultRedisClusterWaitStrategy.java
                Wait.forLogMessage(".*Background AOF rewrite finished successfully.*\\\\s", 1)
            )
            .withTailChildContainers(true)
    }

    fun jdbcUrl() = "jdbc:mysql://${container.getServiceHost(
        properties.mysql.serviceName,
        properties.mysql.port
    )}:${container.getServicePort(
        properties.mysql.serviceName,
        properties.mysql.port
    )}/${properties.mysql.name}"

    fun redisClusterNodes() = "${container.getServiceHost(
        "redis-cluster",
        7000
    )}:${container.getServicePort("redis-cluster", 7000)}"

    class Initializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
        override fun initialize(applicationContext: ConfigurableApplicationContext) {
            try {
                properties = CommonTestContainersProperties.fromEnvironment(applicationContext.environment)
                container.start()

                val jdbcUrl = jdbcUrl()
                val redisClusterNodes = redisClusterNodes()
                log.info("docker containers jdbc.url=$jdbcUrl, redis.cluster.nodes=$redisClusterNodes")

                TestPropertyValues.of(
                    "spring.datasource.url=$jdbcUrl",
                    "spring.redis.cluster.nodes=$redisClusterNodes"
                ).applyTo(applicationContext)

                Runtime.getRuntime().addShutdownHook(Thread {
                    container.stop()
                })
            } catch (e: Exception) {
                container.stop()
                throw IllegalStateException("Docker initialization failed", e)
            }
        }
    }

    private class KDockerComposeContainer(file: File) : DockerComposeContainer<KDockerComposeContainer>(file)
}