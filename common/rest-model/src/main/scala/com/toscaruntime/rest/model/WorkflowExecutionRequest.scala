package com.toscaruntime.rest.model

import play.api.libs.json.Json

import JSONMapStringAnyFormat._

case class WorkflowExecutionRequest(workflowId: String, inputs: Map[String, Any])

object WorkflowExecutionRequest {
  implicit val WorkflowExecutionRequestFormat = Json.format[WorkflowExecutionRequest]
}
