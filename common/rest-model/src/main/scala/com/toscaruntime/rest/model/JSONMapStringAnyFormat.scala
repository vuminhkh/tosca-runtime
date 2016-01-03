package com.toscaruntime.rest.model

import play.api.libs.json._

object JSONMapStringAnyFormat {

  def convertListToJsValue(list: Iterable[Any]): JsArray = {
    Json.toJson(list.map(convertToJsValue).toSeq).as[JsArray]
  }

  def convertJsArrayToList(jsArray: JsArray): Iterable[Any] = {
    jsArray.value.map(convertJsValueToObject)
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
    }
  }

  def convertMapToJsValue(map: Map[String, Any]): JsObject = {
    Json.toJson(map.map {
      case (key, value) => (key, convertToJsValue(value))
    }).as[JsObject]
  }

  def convertToJsValue(obj: Any): JsValue = {
    obj match {
      case map: Map[String, Any] => convertMapToJsValue(map)
      case list: Iterable[Any] => convertListToJsValue(list)
      case string: String => JsString(string)
      case bool: Boolean => JsBoolean(bool)
      case other => JsNumber(other.asInstanceOf[BigDecimal])
    }
  }

  implicit val stringAnyMapFormat = new Format[Map[String, Any]] {

    def writes(map: Map[String, Any]): JsValue = {
      convertMapToJsValue(map)
    }

    override def reads(json: JsValue): JsResult[Map[String, Any]] = {
      JsSuccess(convertJsObjectToMap(json.as[JsObject]))
    }
  }
}
