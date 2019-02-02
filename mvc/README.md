# boot-kotlin-example-mvc

よくあるWebアプリケーションを `spring-boot 2.x + mvc` と `Kotlin` を使っていろいろ試してみる

- `spring-boot 2.x`
  - `kotlinx.coroutine` と `java.util.concurrent.Callable` を使ってみる
- `mybatis 3.5` : 新しく出たので使ってみた `java.util.Optional` 対応したらしいので使ってみたが、Kotlinだとそんなうれしくないが...
- `spring-security`: ちょっと整理のために入れてみる
- `spring-data-redis`: Redis Cluster

とりあえず、MySQL, Redis は Docker で準備する

```
$ docker-compose up -d
```

アプリケーションをコマンドラインから起動する

```
$ ./gradlew bootRun
```

APIへアクセスしてみる

```
$ curl -iks http://localhost:8081/api/user/test0001

HTTP/1.1 200
X-Content-Type-Options: nosniff
X-XSS-Protection: 1; mode=block
Cache-Control: no-cache, no-store, max-age=0, must-revalidate
Pragma: no-cache
Expires: 0
X-Frame-Options: DENY
X-Content-Type-Options: nosniff
Content-Type: application/json;charset=UTF-8
Transfer-Encoding: chunked
Date: Tue, 29 Jan 2019 11:34:13 GMT
```

実行結果

> リクエストがどんな流れで処理されるのか確認してみた。

```
2019-01-29 20:34:13.544  INFO 72410 --- [nio-8081-exec-5] w.PseudoPreAuthenticatedProcessingFilter : PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedPrincipal
2019-01-29 20:34:13.544  INFO 72410 --- [nio-8081-exec-5] w.PseudoPreAuthenticatedProcessingFilter : PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedCredentials
2019-01-29 20:34:13.544  INFO 72410 --- [nio-8081-exec-5] w.PseudoAuthenticationUserDetailsService : AuthenticationUserDetailsService#loadUserDetails
2019-01-29 20:34:13.544  INFO 72410 --- [nio-8081-exec-5] w.PseudoAuthenticationUserDetailsService : AuthenticationUserDetailsService#verifyUserDetailsWithPrincipal
2019-01-29 20:34:13.545  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] oncePerRequestFilter
2019-01-29 20:34:13.546  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] handlerInterceptor#preHandle
2019-01-29 20:34:13.546  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] handlerInterceptor#preHandle
2019-01-29 20:34:13.546  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] taskDecorator(outer)
2019-01-29 20:34:13.546  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] taskDecorator(outer)
2019-01-29 20:34:13.547  INFO 72410 --- [ async-worker-3] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] taskDecorator(inner)
2019-01-29 20:34:13.547  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] handlerInterceptor#afterConcurrentHandlingStarted
2019-01-29 20:34:13.547  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] handlerInterceptor#afterConcurrentHandlingStarted
2019-01-29 20:34:13.547  INFO 72410 --- [ async-worker-3] org.horiga.study.web.UserController      : @AuthenticationPrincipal#accessUser: UserDetailsImpl(id=id@dummy_user)
2019-01-29 20:34:13.547  INFO 72410 --- [nio-8081-exec-5] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] oncePerRequestFilter
2019-01-29 20:34:13.551  INFO 72410 --- [ async-worker-3] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] taskDecorator(inner)
2019-01-29 20:34:13.551  INFO 72410 --- [nio-8081-exec-6] w.PseudoPreAuthenticatedProcessingFilter : PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedPrincipal
2019-01-29 20:34:13.551  INFO 72410 --- [nio-8081-exec-6] w.PseudoPreAuthenticatedProcessingFilter : PseudoPreAuthenticatedProcessingFilter#getPreAuthenticatedCredentials
2019-01-29 20:34:13.551  INFO 72410 --- [nio-8081-exec-6] w.PseudoAuthenticationUserDetailsService : AuthenticationUserDetailsService#loadUserDetails
2019-01-29 20:34:13.553  INFO 72410 --- [nio-8081-exec-6] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] handlerInterceptor#preHandle
2019-01-29 20:34:13.553  INFO 72410 --- [nio-8081-exec-6] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] handlerInterceptor#preHandle
2019-01-29 20:34:13.554  INFO 72410 --- [nio-8081-exec-6] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] handlerInterceptor#postHandle
2019-01-29 20:34:13.554  INFO 72410 --- [nio-8081-exec-6] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] handlerInterceptor#postHandle
2019-01-29 20:34:13.554  INFO 72410 --- [nio-8081-exec-6] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][START] handlerInterceptor#afterCompletion
2019-01-29 20:34:13.554  INFO 72410 --- [nio-8081-exec-6] org.horiga.study.web.Log                 : [75a2b7ef-c016-47a8-a82c-305ce44f43ed][ END ] handlerInterceptor#afterCompletion
```







