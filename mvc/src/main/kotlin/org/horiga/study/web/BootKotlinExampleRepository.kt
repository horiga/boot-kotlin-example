package org.horiga.study.web

import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.Optional

data class User(
    val id: String,
    val name: String,
    val description: String,
    val birthday: LocalDate,
    val createdAt: Instant? = null
)

@Repository
interface UserRepository {

    @Select(
        """
        SELECT id, name, description, birthday, createdAt
        FROM user
        WHERE id = #{id}
    """
    )
    fun findById(@Param("id") id: String): Optional<User>

    //fun findByName(name: String): Collection<User>

    //fun findByBirthday(year: String, month: String, dayOfMonth: String)

    @Insert(
        """
        INSERT INTO user(id, name, description, birthday, createdAt)
        VALUES(#{id}, #{name}, #{description}, #{birthday}, NOW())
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