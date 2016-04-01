package com.toscaruntime.rest.model

import play.api.libs.json._

object JSONMapStringAnyFormat {

  def convertJsArrayToList(jsArray: JsArray): List[Any] = {
    jsArray.value.map(convertJsValueToObject).toList
  }

  def convertJsObjectToMap(jsObject: JsObject): Map[String, Any] = {
    jsObject.value.map {
      case (key, value) => (key, convertJsValueToObject(value))
    }.toMap
  }

  def convertJsValueToObject(jsValue: JsValue): Any = {
    jsValue match {
      case jsObject: JsObject => convertJsObjectToMap(jsObject)
      case jsArray: JsArray => convertJsArrayToList(jsArray)
      case string: JsString => string.value
      case number: JsNumber => number.value
      case bool: JsBoolean => bool.value
      case JsNull => None
    }
  }

  def convertListToJsValue(list: List[Any]): JsArray = {
    JsArray(list.map(convertToJsValue).toSeq).as[JsArray]
  }

  def convertMapToJsValue(map: Map[String, Any]): JsObject = {
    JsObject(map.map {
      case (key, value) => (key, convertToJsValue(value))
    }).as[JsObject]
  }

  def convertToJsValue(obj: Any): JsValue = {
    obj match {
      case map: Map[String, Any] => convertMapToJsValue(map)
      case list: List[Any] => convertListToJsValue(list)
      case string: String => JsString(string)
      case bool: Boolean => JsBoolean(bool)
      case None => JsNull
      case other => JsNumber(BigDecimal(other.toString))
    }
  }

  implicit val stringAnyMapFormat = new Format[Map[String, Any]] {

    override def reads(json: JsValue): JsResult[Map[String, Any]] = {
      JsSuccess(convertJsObjectToMap(json.as[JsObject]))
    }

    def writes(map: Map[String, Any]): JsValue = {
      convertMapToJsValue(map)
    }
  }
}
