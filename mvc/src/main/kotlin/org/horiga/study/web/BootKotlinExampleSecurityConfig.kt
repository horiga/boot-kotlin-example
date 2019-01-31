package org.horiga.study.web

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.net.HttpHeaders
import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.builders.WebSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.userdetails.AuthenticationUserDetailsService
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationProvider
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

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
        http.cors()
            .and()
            .authorizeRequests()
            .antMatchers(*properties.authPaths.toTypedArray()) // 1.
            .authenticated()
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

        http.addFilter(PseudoPreAuthenticatedProcessingFilter().apply {
            setAuthenticationManager(authenticationManager())
        })
            .antMatcher("/api/**") // NOTE: ここのFilterに対するurl-patternは`1.`のantMatchersは適用されない
            .exceptionHandling()
            .authenticationEntryPoint(PseudoAuthenticationEntryPoint(objectMapper))
            .accessDeniedHandler(PseudoUnauthorizedHandler(objectMapper))

        http.formLogin().disable()
        http.httpBasic().disable()
        http.csrf().disable()
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

class UserPrincipal(
    val original: HttpServletRequest,
    val accessToken: String,
    val error: AuthenticationException? = null
)

class PseudoAuthenticationUserDetailsService :
    AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {

    companion object {
        val log = LoggerFactory.getLogger(PseudoAuthenticationUserDetailsService::class.java)!!
        const val attributeName = "userDetails"
    }

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

        if (principal.error != null) throw principal.error

        // Authorization: Bearer <token>
        return principal.accessToken.trim().split(Regex("\\s+")).let {
            if (it.size < 2)
                throw BadCredentialsException("Bad credentials, ${principal.accessToken}")

            // TODO: ここでユーザ認証してあげる
            // - トークンの有効期限切れ、そもそも有効かとか、もろもろ、だめなら AuthenticationException 関連をスローすると
            //   authenticationEntryPoint に登録したハンドラで後処理されるみたいだ

            if (it[1].equals("access_denied", true))
                throw AccessDeniedException("access_denied")

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