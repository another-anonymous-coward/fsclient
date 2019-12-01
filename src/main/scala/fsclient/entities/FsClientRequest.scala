package fsclient.entities

import cats.effect.Effect
import fsclient.config.AppConsumer
import fsclient.oauth.FsHeaders
import fsclient.utils.Logger._
import io.circe.Encoder
import org.http4s.Method.{DefaultMethodWithBody, SafeMethodWithBody}
import org.http4s._

trait FsClientPlainRequest {
  def uri: Uri
  def method: Method
  def headers: Headers = Headers.empty
  def toHttpRequest[F[_]: Effect](consumer: AppConsumer): Request[F] =
    logRequest {
      Request()
        .withMethod(method)
        .withUri(uri)
        .withHeaders(headers.++(Headers.of(FsHeaders.userAgent(consumer.userAgent))))
    }
}

object FsClientPlainRequest {
  trait Get extends FsClientPlainRequest {
    override val method: SafeMethodWithBody = Method.GET
  }
  trait Post extends FsClientPlainRequest {
    override val method: DefaultMethodWithBody = Method.POST
  }
}

trait FsClientRequestWithBody[Body] {
  def uri: Uri
  def method: Method
  def headers: Headers = Headers.empty
  def body: Body
  def toHttpRequest[F[_]: Effect](
    consumer: AppConsumer
  )(implicit requestBodyEncoder: EntityEncoder[F, Body]): Request[F] =
    logRequest(
      body,
      Request()
        .withMethod(method)
        .withUri(uri)
        .withEntity(body)
        .withHeaders(headers.++(Headers.of(FsHeaders.userAgent(consumer.userAgent))))
    )
}

object FsClientRequestWithBody {
  trait Get[Body] extends FsClientRequestWithBody[Body] {
    override val method: SafeMethodWithBody = Method.GET
  }
  trait Post[Body] extends FsClientRequestWithBody[Body] {
    override val method: DefaultMethodWithBody = Method.POST
  }
  trait PostJson[Body] extends FsClientRequestWithBody[Body] {
    override val method: DefaultMethodWithBody = Method.POST
    import org.http4s.circe._
    implicit def entityJsonEncoder[F[_]: Effect](implicit encoder: Encoder[Body]): EntityEncoder[F, Body] =
      jsonEncoderOf[F, Body]
  }
}