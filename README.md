[![CircleCI](https://circleci.com/gh/bartholomews/fsclient/tree/master.svg?style=svg)](https://circleci.com/gh/bartholomews/fsclient/tree/master)
[![codecov](https://codecov.io/gh/bartholomews/fsclient/branch/master/graph/badge.svg)](https://codecov.io/gh/bartholomews/fsclient)
[![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
[![License: Unlicense](https://img.shields.io/badge/license-Unlicense-black.svg)](http://unlicense.org/)
<a href="https://typelevel.org/cats/"><img src="https://typelevel.org/cats/img/cats-badge.svg" height="40px" align="right" alt="Cats friendly" /></a>

# fsclient

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.bartholomews/fsclient_2.13/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.bartholomews/fsclient_2.13)

🔧 **This project is still early stage and ery much WIP / experimental** 🔧  

```
libraryDependencies += "io.bartholomews" %% "fsclient" % "0.0.3"
```

*Opinionated* http client on top of http4s/fs2

Motivation for this project is to 
- play around with the Typelevel stack
- set up oAuth handling, logging, codecs patterns for api clients

```scala
import cats.effect.{ContextShift, IO}
import io.bartholomews.fsclient.client.FsClientV1
import io.bartholomews.fsclient.config.UserAgent
import io.bartholomews.fsclient.entities._
import io.bartholomews.fsclient.entities.oauth.{ClientCredentials, SignerV1}
import io.bartholomews.fsclient.requests.{FsSimpleJson, FsSimpleRequest}
import io.bartholomews.fsclient.utils.HttpTypes.HttpResponse
import io.circe.{Codec, Json}
import org.http4s.Uri

import scala.concurrent.ExecutionContext

object Example extends App {

  implicit val ec: ExecutionContext = ExecutionContext.global
  implicit val cs: ContextShift[IO] = IO.contextShift(ec)

  // will add header `User-Agent: myapp/0.0.1-SNAPSHOT (+com.github.bartholomews)` to all requests
  val userAgent = UserAgent(
    appName = "myapp",
    appVersion = Some("0.0.1-SNAPSHOT"),
    appUrl = Some("com.github.bartholomews")
  )

  val consumer = org.http4s.client.oauth1.Consumer(
    key = "CONSUMER_KEY",
    secret = "CONSUMER_SECRET"
  )

  // Sign with consumer key/secret, but without token
  // Otherwise you can use `AuthVersion.V1.OAuthToken`
  val signer = ClientCredentials(consumer)

  // Define your expected response entity
  case class SampleEntity(userId: Long, id: Long, title: String, body: String)
  object SampleEntity {
    implicit val codec: Codec[SampleEntity] = io.circe.generic.semiauto.deriveCodec
  }

  val postsUri: Uri = org.http4s.Uri.unsafeFromString("http://jsonplaceholder.typicode.com/posts")

  /*
    `FsRequest` has three type parameters:
      Body: the request body
      Raw: the raw response content-type
      Res: the decoded response into your own type
     Depending on the types you will be forced to add the request body
     and have the right implicits in scope for the codecs.
    `FsSimple` requests will use the client signer,
    `FsAuth` requests require their own signer.
   */
  val getEntities: FsSimpleRequest[Nothing, Json, List[SampleEntity]] = new FsSimpleJson.Get[List[SampleEntity]] {
    override val uri: Uri = postsUri
  }

  val postEntity: FsSimpleRequest[SampleEntity, Json, Unit] =
    new FsSimpleJson.Post[SampleEntity, Unit] {
      override val uri: Uri = org.http4s.Uri.unsafeFromString("http://jsonplaceholder.typicode.com/posts")
      override def requestBody: SampleEntity = SampleEntity(userId = 1L, id = 1L, title = "A sample entity", body = "_")
    }

  // An OAuth v1 client with ClientCredentials signer and cats IO
  val client: FsClientV1[IO, SignerV1] = FsClientV1(userAgent, signer)

  // Run your request with the client for your effect
  val res: IO[HttpResponse[List[SampleEntity]]] = for {
    _ <- postEntity.runWith(client)
    maybeEntity <- getEntities.runWith(client)
  } yield maybeEntity

  val response = res.unsafeRunSync()
  println(response.headers)
  println(response.status)
  response.foldBody(
    {
      case ErrorBodyString(error) => println(error)
      case ErrorBodyJson(error)   => println(error.spaces2)
    },
    todo => println(todo.head.title)
  )
}
```

## CircleCI deployment

### Verify local configuration
https://circleci.com/docs/2.0/local-cli/
```bash
circleci config validate
```

### CI/CD Pipeline

This project is using [sbt-ci-release](https://github.com/olafurpg/sbt-ci-release) plugin:
 - Every push to master will trigger a snapshot release.  
 - In order to trigger a regular release you need to push a tag:
 
    ```bash
    ./scripts/release.sh v1.0.0
    ```
 
 - If for some reason you need to replace an older version (e.g. the release stage failed):
 
    ```bash
    TAG=v1.0.0
    git push --delete origin ${TAG} && git tag --delete ${TAG} \
    && ./scripts/release.sh ${TAG}
    ```