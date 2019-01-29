package org.horiga.study.web

import org.slf4j.LoggerFactory
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
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
    val authPaths: Collection<String> = listOf("/**"),
    val ignoringPaths: Collection<String> = listOf("")
)

@EnableWebSecurity
@EnableConfigurationProperties(BootKotlinExampleWebSecurityProperties::class)
class BootKotlinExampleSecurityConfig(
    val properties: BootKotlinExampleWebSecurityProperties
) : WebSecurityConfigurerAdapter() {

    override fun configure(http: HttpSecurity) {
        http.cors()
            .and()
            .authorizeRequests()
            .antMatchers(*properties.authPaths.toTypedArray())
            .authenticated()
            .and()
            .sessionManagement()
            .sessionCreationPolicy(SessionCreationPolicy.STATELESS)

        http.addFilter(PseudoPreAuthenticatedProcessingFilter().apply {
            setAuthenticationManager(authenticationManager())
        })
            .exceptionHandling()
            .authenticationEntryPoint(UnauthorizedEntryPoint())
            .accessDeniedHandler(UnauthorizedHandler())

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

class UnauthorizedEntryPoint : AuthenticationEntryPoint {

    companion object {
        val log = LoggerFactory.getLogger(UnauthorizedEntryPoint::class.java)!!
    }

    override fun commence(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        authException: AuthenticationException?
    ) {
        log.info("UnauthorizedEntryPoint#commence", authException)
        response!!.status = HttpStatus.UNAUTHORIZED.value()
    }
}

class UnauthorizedHandler : AccessDeniedHandler {

    companion object {
        val log = LoggerFactory.getLogger(UnauthorizedHandler::class.java)!!
    }

    override fun handle(
        request: HttpServletRequest?,
        response: HttpServletResponse?,
        accessDeniedException: AccessDeniedException?
    ) {
        log.info("UnauthorizedHandler#handle", accessDeniedException)
        TODO("not implemented")
    }
}

class PseudoAuthenticationUserDetailsService :
    AuthenticationUserDetailsService<PreAuthenticatedAuthenticationToken> {
    override fun loadUserDetails(token: PreAuthenticatedAuthenticationToken?): UserDetails {
        TODO("not implemented")
    }
}

class PseudoPreAuthenticatedProcessingFilter : AbstractPreAuthenticatedProcessingFilter() {

    companion object {
        val log = LoggerFactory.getLogger(PseudoPreAuthenticatedProcessingFilter::class.java)!!
    }

    override fun getPreAuthenticatedCredentials(request: HttpServletRequest?): Any {
        log.info("PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedCredentials")
        TODO("not implemented")
    }

    override fun getPreAuthenticatedPrincipal(request: HttpServletRequest?): Any {
        log.info("PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedPrincipal")
        TODO("not implemented")
    }
}