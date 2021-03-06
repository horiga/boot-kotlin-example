package org.horiga.study.web

import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

data class User(
    val id: String,
    val name: String,
    val description: String,
    val role: String,
    val birthday: LocalDate,
    val createdAt: Instant? = null
)

@Repository
interface UserRepository {

    @Select(
        """
        SELECT id, name, description, role, birthday, created_at
        FROM user
        WHERE id = #{id}
    """
    )
    fun findById(@Param("id") id: String): Optional<User>

    //fun findByName(name: String): Collection<User>

    //fun findByBirthday(year: String, month: String, dayOfMonth: String)

    @Insert(
        """
        INSERT INTO user(id, name, description, role, birthday, created_at)
        VALUES(#{id}, #{name}, #{description}, #{role}, #{birthday}, NOW())
    """
    )
    fun insert(user: User)

    @Delete(
        """
        DELETE FROM user WHERE id = #{id}
    """
    )
    fun delete(id: String)
}
