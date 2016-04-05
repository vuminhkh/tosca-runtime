package com.toscaruntime.it.util

import akka.pattern._
import com.toscaruntime.it.Context
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.ws.WSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

object URLChecker extends LazyLogging {

  def checkURL(url: String, expectedStatus: Int, ignoredStatuses: Set[Int], timeout: Duration, bodyMustContain: Option[String] = None): WSResponse = {
    Await.result(doCheckURL(url, expectedStatus, ignoredStatuses, timeout, bodyMustContain), 5 minutes)
  }

  def checkURLNonAvailable(url: String) = {
    Context.client.wsClient.url(url).get().flatMap { response =>
      Future.failed(new AssertionError("Don't expect URL to be available"))
    }.recoverWith {
      case e: Exception => Future.successful(s"URL is not available as expected ${e.getMessage}")
    }
  }

  def doCheckURL(url: String, expectedStatus: Int, ignoredStatuses: Set[Int], timeout: Duration, bodyMustContain: Option[String]): Future[WSResponse] = {
    Context.client.wsClient.url(url).get().flatMap { response =>
      if (expectedStatus == response.status) {
        logger.info(s"Got expected status $expectedStatus for $url")
        if (bodyMustContain.isEmpty) Future.successful(response)
        else if (response.body.contains(bodyMustContain.get)) Future.successful(response)
        else Future.failed(new AssertionError(s"Body \n${response.body}\nDo not contain expected text ${bodyMustContain.get}"))
      } else if (ignoredStatuses.contains(expectedStatus)) {
        logger.info(s"Got status $expectedStatus for $url, retry")
        after(2 seconds, Context.client.system.scheduler)(doCheckURL(url, expectedStatus, ignoredStatuses, timeout, bodyMustContain))
      } else {
        val errorMessage = s"Error perform request on url $url, got ${response.status} and message ${response.body}"
        logger.error(errorMessage)
        Future.failed(new AssertionError(errorMessage))
      }
    }.recoverWith {
      case e: Exception =>
        logger.info(s"Server at url $url is not up, retry in 2 seconds")
        after(2 seconds, Context.client.system.scheduler)(doCheckURL(url, expectedStatus, ignoredStatuses, timeout, bodyMustContain))
    }
  }

}
