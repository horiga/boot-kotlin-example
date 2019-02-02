package org.horiga.study.web

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.future
import org.slf4j.LoggerFactory
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.Callable
import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Pattern
import javax.validation.constraints.Size

data class Message(
    val txid: String = ""
)

data class ReplyMessage(
    val id: String,
    val name: String
)

data class PostMessage(
    @field: NotBlank
    @field: Size(max = 30)
    val name: String,

    @field: Size(max = 500)
    val description: String = "",

    @field: NotBlank
    val role: String = Authorities.GUEST_ROLE.authority,

    @field: NotBlank
    @field: Pattern(
        regexp = "(19[0-9]{2}|2[0-9]{3})-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])", // YYYY-MM-DD, 1900/01/01 ~ 2999/12/31
        message = "{validation.PostMessage.birthday.Pattern.message}"
    )
    val birthday: String = "1970-1-1"
)

data class PostReplyMessage(
    val id: String,
    val age: Int
)

@RestController
@RequestMapping("/api/coroutine")
class CoroutineController(
    private val mvcDispatcher: CoroutineDispatcher
) {
    companion object {
        val log = LoggerFactory.getLogger(CoroutineController::class.java)!!
        val birthdayFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd")!!
    }

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

    @PostMapping("/{id}")
    fun postMessage(@PathVariable id: String, @RequestBody @Valid message: PostMessage) =
        GlobalScope.future(mvcDispatcher) {
            log.info("id: $id, postMessage: $message")
            PostReplyMessage(id, message.birthday.let {
                // calculate age from birthday.
                ChronoUnit.YEARS.between(LocalDate.parse(it, birthdayFormat), LocalDate.now()).toInt()
            })
        }
}

@RestController
@RequestMapping("/api/callable")
class CallableController {

    @GetMapping
    fun get(
        @RequestParam(value = "name", required = false, defaultValue = "") name: String,
        message: Message
    ) = Callable<ReplyMessage> {
        try {
            Log.start("Controller", message.txid)
            ReplyMessage(message.txid, name)
        } finally {
            Log.end("Controller", message.txid)
        }
    }
}

@RestController
@RequestMapping("/api/user")
class UserController(
    val mvcDispatcher: CoroutineDispatcher,
    val userService: UserService
) {
    companion object {
        val log = LoggerFactory.getLogger(UserController::class.java)!!
    }

    @GetMapping("{id}")
    fun findById(@AuthenticationPrincipal accessUser: UserDetails, @PathVariable id: String) =
    //Callable<User> {
        GlobalScope.future(mvcDispatcher) {
            log.info("@AuthenticationPrincipal#accessUser: $accessUser")
            userService.findById(id)
        }

    @PostMapping
    fun addUser(@RequestBody @Valid message: PostMessage) =
    //Callable<User> {
        GlobalScope.future(mvcDispatcher) {
            userService.addUser(message)
        }

    @GetMapping("{id}/role")
    fun getRoleAuthority(@AuthenticationPrincipal accessUser: UserDetails, @PathVariable id: String) =
        GlobalScope.future(mvcDispatcher) {
            userService.getRoleAuthority(id).get()
        }
}

open class NotFoundUserException(
    private val id: String,
    message: String = "user is not founded. id=$id"
) :
    Exception(message, null)