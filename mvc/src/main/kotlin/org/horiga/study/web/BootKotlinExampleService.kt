/*
 * Copyright (c) 2019 LINE Corporation. All rights reserved.
 * LINE Corporation PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package org.horiga.study.web

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.security.core.GrantedAuthority
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.util.Optional
import java.util.UUID

@Service
class UserService(
    private val userRepository: UserRepository,
    private val redisTemplate: ReactiveRedisTemplate<String, String>
) {

    companion object {
        val log = LoggerFactory.getLogger(UserService::class.java)!!
        val COMMAND_BLOCK_TIMEOUT_MILLIS = Duration.ofMillis(10000)!!
    }

    @Throws(NotFoundUserException::class)
    fun findById(id: String): User =
        userRepository.findById(id).orElseThrow { NotFoundUserException(id) }

    @Transactional(rollbackFor = [Exception::class])
    fun addUser(message: PostMessage) =
        User(
            UUID.randomUUID().toString(),
            message.name,
            message.description,
            message.role,
            message.birthday.split("-").let { ymd ->
                LocalDate.of(ymd[0].toInt(), ymd[1].toInt(), ymd[2].toInt())
            }).let { user ->
            userRepository.insert(user)
            redisTemplate.opsForValue().set(user.id, user.role, Duration.ofHours(3))
            user
        }

    fun getRoleAuthority(id: String): Optional<GrantedAuthority> =
        Optional.ofNullable(Authorities.authority(
            redisTemplate.opsForValue().get(id)
                .doOnError { cause ->
                    log.error("Failed to get role authority from Redis cluster", cause)
                    throw cause
                }
                .blockOptional(COMMAND_BLOCK_TIMEOUT_MILLIS).orElseGet {
                    userRepository.findById(id).takeIf { it.isPresent }?.let {
                        val user = it.get()
                        log.info("Retrieve from RDB. id=${user.id}")
                        redisTemplate.opsForValue().set(user.id, user.role, Duration.ofHours(3))
                        user.role
                    } ?: Authorities.GUEST_ROLE.authority
                })
        )
}