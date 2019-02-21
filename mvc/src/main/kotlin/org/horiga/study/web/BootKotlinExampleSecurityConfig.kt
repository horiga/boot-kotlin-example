package org.horiga.study.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.HttpHeaders
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.PermissionEvaluator
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.method.configuration.GlobalMethodSecurityConfiguration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import org.springframework.security.web.csrf.CookieCsrfTokenRepository
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import java.io.Serializable
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

object Authorities {

    val ADMIN_ROLE = SimpleGrantedAuthority("ADMIN")
    val DEVELOPER_ROLE = SimpleGrantedAuthority("DEVELOPER")
    val OPERATOR_ROLE = SimpleGrantedAuthority("OPERATOR")
    val GUEST_ROLE = SimpleGrantedAuthority("GUEST")

    fun all() = listOf<GrantedAuthority>(ADMIN_ROLE, DEVELOPER_ROLE, OPERATOR_ROLE, GUEST_ROLE)

    fun authority(role: String): GrantedAuthority? =
        all().firstOrNull { it.authority.equals(role, true) }
}

data class UserDetailsImpl(
    val id: String
) : UserDetails {
    override fun getAuthorities(): MutableCollection<out GrantedAuthority> = mutableListOf()

    override fun isEnabled(): Boolean = true

    override fun getUsername(): String = id

    override fun isCredentialsNonExpired(): Boolean = true

    override fun getPassword(): String = ""

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true
}

class UserPrincipal(
    val original: HttpServletRequest,
    val accessToken: String, // credentials
    val error: AuthenticationException? = null
)

@ConfigurationProperties(prefix = "app.webmvc.security")
data class BootKotlinExampleWebSecurityProperties(
    val authPaths: Collection<String> = listOf("/api/**"),
    val ignoringPaths: Collection<String> = listOf()
)

@EnableWebSecurity
@EnableConfigurationProperties(BootKotlinExampleWebSecurityProperties::class)
class BootKotlinExampleSecurityConfig(
    val properties: BootKotlinExampleWebSecurityProperties,
    val objectMapper: ObjectMapper
) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {

        http.authorizeRequests()
            .antMatchers("/hello/**").permitAll()
            //.antMatchers(*properties.authPaths.toTypedArray()) // 1.
            .anyRequest().authenticated()
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

        // CORS
        http.cors()
            .configurationSource(corsConfigurationSource())

        // CSRF
//        http.csrf().disable()
        http.csrf().csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            .takeIf { properties.ignoringPaths.isNotEmpty() }?.let {
                it.ignoringAntMatchers(*properties.ignoringPaths.toTypedArray())
            }

        http.addFilter(PseudoPreAuthenticatedProcessingFilter().apply {
            setAuthenticationManager(authenticationManager())
        })
            .antMatcher("/api/**") // NOTE: ここのFilterに対するurl-patternは`1.`のantMatchersは適用されない
            .exceptionHandling()
            .authenticationEntryPoint(PseudoAuthenticationEntryPoint(objectMapper))
            .accessDeniedHandler(PseudoUnauthorizedHandler(objectMapper))

        http.formLogin().disable()
        http.httpBasic().disable()

    }

    override fun configure(web: WebSecurity?) {
        properties.ignoringPaths.takeIf { it.isNotEmpty() }?.let {
            web!!.ignoring().antMatchers(*it.toTypedArray())
        }
    }

    override fun configure(auth: AuthenticationManagerBuilder?) {
        auth!!.authenticationProvider(preAuthenticatedAuthenticationProvider())
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource =
        UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", CorsConfiguration().apply {
                addAllowedOrigin("https://a-site.com")
                allowedMethods =
                    listOf(HttpMethod.GET, HttpMethod.POST, HttpMethod.PUT, HttpMethod.DELETE).map { it.name }
                allowCredentials = true
            })
        }

    @Bean
    fun preAuthenticatedAuthenticationProvider(): PreAuthenticatedAuthenticationProvider =
        PreAuthenticatedAuthenticationProvider().apply {
            setPreAuthenticatedUserDetailsService(PseudoAuthenticationUserDetailsService())
        }
}

class PseudoAuthenticationEntryPoint(private val objectMapper: ObjectMapper) : AuthenticationEntryPoint {

    companion object {
        val log = LoggerFactory.getLogger(PseudoAuthenticationEntryPoint::class.java)!!
    }

    override fun commence(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authException: AuthenticationException?
    ) {
        log.info("PseudoAuthenticationEntryPoint#commence", authException)
        response!!.status = HttpStatus.UNAUTHORIZED.value()
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
        mapOf("error_message" to authException?.message)
        response.writer.print(objectMapper.writeValueAsString(mapOf("error_message" to authException?.message)))
    }
}

class PseudoUnauthorizedHandler(private val objectMapper: ObjectMapper) : AccessDeniedHandler {

    companion object {
        val log = LoggerFactory.getLogger(PseudoUnauthorizedHandler::class.java)!!
    }

    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        accessDeniedException: AccessDeniedException?
    ) {
        log.info("UnauthorizedHandler#handle", accessDeniedException)
        response!!.status = HttpStatus.FORBIDDEN.value()
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_UTF8_VALUE)
        mapOf("error_message" to accessDeniedException?.message)
        response.writer.print(objectMapper.writeValueAsString(mapOf("error_message" to accessDeniedException?.message)))
    }
}

class PseudoAuthenticationUserDetailsService :
    AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

    companion object {
        val log = LoggerFactory.getLogger(PseudoAuthenticationUserDetailsService::class.java)!!
        const val attributeName = "userDetails"
    }

    @Throws(AuthenticationException::class)
    override fun loadUserDetails(token: PreAuthenticatedAuthenticationToken?): UserDetails {

        // PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedCredentials
        val credentials = token!!.credentials as String
        // PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedPrincipal
        val principal = token.principal as UserPrincipal

        log.info("AuthenticationUserDetailsService#loadUserDetails, credentials=$credentials, principal=$principal")

        return principal.original.getAttribute(attributeName) as? UserDetails
                ?: loadUserDetailsWithUserPrincipal(principal).let {
                    principal.original.setAttribute(attributeName, it)
                    it
                }
    }

    @Throws(AuthenticationException::class)
    private fun loadUserDetailsWithUserPrincipal(principal: UserPrincipal): UserDetails {
        log.info("AuthenticationUserDetailsService#loadUserDetailsWithUserPrincipal")

        if (principal.error != null) {
            log.warn("Principal errors", principal.error)
            throw principal.error
        }

        // Authorization: Bearer <token>
        return principal.accessToken.trim().split(Regex("\\s+")).let {
            if (it.size < 2)
                throw BadCredentialsException("Bad credentials, ${principal.accessToken}")

            // TODO: ここでユーザ認証してあげる
            // - トークンの有効期限切れ、そもそも有効かとか、もろもろ、だめなら AuthenticationException 関連をスローすると
            //   authenticationEntryPoint に登録したハンドラで後処理されるみたいだ

            if (it[1].equals("access_denied", true))
                throw AccessDeniedException("access_denied") // こいつは PseudoUnauthorizedHandler ではハンドリングされない

            log.info("credential validation: scheme=[${it[0]}] token=[${it[1]}]")

            UserDetailsImpl("id@${it[1]}")
        }
    }
}

class PseudoPreAuthenticatedProcessingFilter : AbstractPreAuthenticatedProcessingFilter() {

    companion object {
        val log = LoggerFactory.getLogger(PseudoPreAuthenticatedProcessingFilter::class.java)!!
    }

    override fun getPreAuthenticatedCredentials(request: HttpServletRequest?): Any {
        log.info("PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedCredentials")
        return request!!.getHeader("Authorization") ?: ""
    }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest?): Any {
        log.info("PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedPrincipal")
        val accessToken = request!!.getHeader("Authorization") ?: ""
        return if (accessToken.isNotBlank()) UserPrincipal(request, accessToken)
        else {
            UserPrincipal(
                request,
                "",
                AuthenticationCredentialsNotFoundException("There is no 'Authorization' header or values")
            )
        }
    }
}

@Configuration
@EnableGlobalMethodSecurity(prePostEnabled = true)
class MethodSecurityConfig : GlobalMethodSecurityConfiguration() {
    override fun createExpressionHandler(): MethodSecurityExpressionHandler =
        DefaultMethodSecurityExpressionHandler().apply {
            setPermissionEvaluator(RolePermissionEvaluator())
        }
}

class RolePermissionEvaluator : PermissionEvaluator {

    companion object {
        val log = LoggerFactory.getLogger(RolePermissionEvaluator::class.java)!!
    }

    override fun hasPermission(
        authentication: Authentication?,
        targetId: Serializable?,
        targetType: String?,
        permission: Any?
    ): Boolean {
        log.info("RolePermissionEvaluator#hasPermission(Authentication, Serializable, String, Any)")
        TODO("not implemented")
    }

    override fun hasPermission(
        authentication: Authentication?,
        targetDomainObject: Any?,
        permission: Any?
    ): Boolean {
        log.info("RolePermissionEvaluator#hasPermission(Authentication, Any, Any)")
        TODO("not implemented")
    }
}