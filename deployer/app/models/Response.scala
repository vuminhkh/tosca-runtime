package models

import play.api.libs.functional.syntax._
import play.api.libs.json._

object RestStatus {
  val SUCCESS = "success"
  val FAIL = "fail"
  val ERROR = "error"
}

case class RestResponse[T](status: String, data: Option[T] = None, message: Option[String] = None, code: Option[Int] = None)

object RestResponse {

  def success[T](data: Option[T] = None): RestResponse[T] = {
    RestResponse(status = RestStatus.SUCCESS, data = data)
  }

  def fail[T](data: T): RestResponse[T] = {
    RestResponse(status = RestStatus.FAIL, data = Some(data))
  }

  def error[T](data: Option[T] = None, message: String, code: Option[Int] = None): RestResponse[T] = {
    RestResponse(RestStatus.ERROR, data, Some(message), code)
  }

  implicit def restResponseWrite[T: Writes]: Writes[RestResponse[T]] = (
    (__ \ 'status).write[String] and
      (__ \ 'data).write[Option[T]] and
      (__ \ 'message).write[Option[String]] and
      (__ \ 'code).write[Option[Int]]
    )(unlift(RestResponse.unapply[T]))
}
