package com.mkv.tosca.compiler.generator

object Util {

  def methodHasInput(method: Method) = {
    method.functionInputs.nonEmpty || method.scalarInputs.nonEmpty
  }
}
