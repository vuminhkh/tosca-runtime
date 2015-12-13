package com.toscaruntime.util

object CodeGeneratorUtil {

  def getGeneratedMethodName(interfaceName: String, operationName: String) = {
    interfaceName match {
      case "Standard" | "tosca.interfaces.node.lifecycle.Standard" => operationName
      case "Configure" | "tosca.interfaces.relationship.Configure" => operationName
      case _ => interfaceName.replaceAll("\\.", "_") + "_" + operationName
    }
  }

}
