package com.toscaruntime.it.util

import akka.pattern._
import com.toscaruntime.it.Context
import com.typesafe.scalalogging.LazyLogging
import play.api.libs.ws.WSResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object URLChecker extends LazyLogging {

  def checkURL(url: String, expectedStatus: Int, ignoredStatuses: Set[Int], timeout: Duration): WSResponse = {
    Await.result(doCheckURL(url, expectedStatus, ignoredStatuses, timeout), 5 minutes)
  }

  def doCheckURL(url: String, expectedStatus: Int, ignoredStatuses: Set[Int], timeout: Duration): Future[WSResponse] = {
    Context.client.wsClient.url(url).get().flatMap { response =>
      if (expectedStatus == response.status) {
        logger.info(s"Got expected status $expectedStatus for $url")
        Future.successful(response)
      } else if (ignoredStatuses.contains(expectedStatus)) {
        logger.info(s"Got status $expectedStatus for $url, retry")
        after(2 seconds, Context.client.system.scheduler)(doCheckURL(url, expectedStatus, ignoredStatuses, timeout))
      } else {
        val errorMessage = s"Error perform request on url $url, got ${response.status} and message ${response.body}"
        logger.error(errorMessage)
        Future.failed(new AssertionError(errorMessage))
      }
    }.recoverWith {
      case e: Exception =>
        logger.info(s"Server at url $url is not up, retry in 2 seconds")
        after(2 seconds, Context.client.system.scheduler)(doCheckURL(url, expectedStatus, ignoredStatuses, timeout))
    }
  }

}
