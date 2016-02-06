package com.toscaruntime.rest.model

import com.toscaruntime.rest.model.JSONMapStringAnyFormat._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{MustMatchers, WordSpec}
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

class DeploymentDetailsSpec extends WordSpec with MustMatchers with LazyLogging {

  "Map String Any" must {
    "be able to be converted to JsObject and vice versa" in {
      val map = Map(
        "testSimpleProp" -> "testSimpleValue",
        "testComplexProp" -> Map(
          "key" -> "value",
          "listKey" -> List("a", "b", "c")
        )
      )
      val serialized = Json.toJson(map)
      logger.info(s"Serialized map $serialized")
      serialized.asOpt[JsObject] must be(defined)
      serialized.as[JsObject].value.get("testSimpleProp") must be(Some(JsString("testSimpleValue")))
      val complexProp = serialized.as[JsObject].value.get("testComplexProp")
      complexProp must be(defined)
      complexProp.get.asOpt[JsObject] must be(defined)
      complexProp.get.as[JsObject].value.get("listKey") must be(Some(JsArray(List(JsString("a"), JsString("b"), JsString("c")))))
      val convertedMap = Json.fromJson[Map[String, Any]](serialized).get
      convertedMap must be(map)
    }
  }

  "DeploymentDetails" must {
    "be able to be converted to JsValue and vice versa" in {
      val details = DeploymentDetails(
        name = "test",
        nodes = List(
          Node(
            id = "testNode",
            properties = Map(
              "testSimpleProp" -> "testSimpleValue",
              "testComplexProp" -> Map(
                "key" -> "value",
                "listKey" -> List("a", "b", "c")
              )
            ),
            instances = List(
              Instance(
                id = "testNodeInstance",
                state = "started",
                attributes = Map(
                  "ip" -> "1.2.3.4",
                  "networks" -> List(
                    Map(
                      "id" -> "net1",
                      "cird" -> "6.7.8.9/24"
                    ),
                    Map(
                      "id" -> "net2",
                      "cird" -> "6.7.8.10/24"
                    )
                  )
                )
              )
            )
          )
        ),
        relationships = List(
          RelationshipNode(
            sourceNodeId = "testSource",
            targetNodeId = "testTarget",
            properties = Map.empty,
            relationshipInstances = List(
              RelationshipInstance(
                sourceInstanceId = "testSourceInstanceId",
                targetInstanceId = "testTargetInstanceId",
                state = "postConfiguredSource",
                attributes = Map.empty
              )
            )
          )
        ),
        outputs = Map(
          "a" -> "b",
          "list" -> List(1, 2, 3, 4),
          "mapOfList" -> Map(
            "a" -> List(2, 4),
            "b" -> List(1, 3)
          )
        )
      )
      val serialized = Json.toJson(details)
      logger.info(s"Serialized deployment details $serialized")
      val convertedDetails = Json.fromJson[DeploymentDetails](serialized).get
      convertedDetails must be(details)
    }
  }
}
