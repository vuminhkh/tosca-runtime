package com.toscaruntime.util

import com.toscaruntime.constant.ToscaInterfaceConstant._

object CodeGeneratorUtil {

  def getGeneratedMethodName(interfaceName: String, operationName: String) = {
    normalizeInterfaceName(interfaceName) match {
      case NODE_STANDARD_INTERFACE => operationName
      case RELATIONSHIP_STANDARD_INTERFACE => operationName
      case _ => interfaceName.replaceAll("\\.", "_") + "_" + operationName
    }
  }

  def normalizeInterfaceName(interfaceName: String) = {
    interfaceName match {
      case "standard" | "tosca.interfaces.node.lifecycle.Standard" => NODE_STANDARD_INTERFACE
      case "configure" | "tosca.interfaces.relationship.Configure" => RELATIONSHIP_STANDARD_INTERFACE
      case other => other
    }
  }
}
