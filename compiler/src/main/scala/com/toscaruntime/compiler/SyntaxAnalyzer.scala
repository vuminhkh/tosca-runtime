package com.toscaruntime.compiler

import com.toscaruntime.compiler.parser.YamlParser
import com.toscaruntime.compiler.tosca._

object SyntaxAnalyzer extends YamlParser {

  val definitions_version_token: String = "tosca_definitions_version"
  val template_name_token: String = "template_name"
  val template_version_token: String = "template_version"
  val template_author_token: String = "template_author"
  val description_token: String = "description"
  val artifact_types_token: String = "artifact_types"
  val relationship_types_token: String = "relationship_types"
  val capability_types_token: String = "capability_types"
  val node_types_token: String = "node_types"
  val derived_from_token: String = "derived_from"
  val abstract_token: String = "abstract"
  val tags_token: String = "tags"
  val properties_token: String = "properties"
  val attributes_token: String = "attributes"
  val requirements_token: String = "requirements"
  val capabilities_token: String = "capabilities"
  val artifacts_token: String = "artifacts"
  val interfaces_token: String = "interfaces"
  val type_token: String = "type"
  val required_token: String = "required"
  val default_token: String = "default"
  val constraints_token: String = "constraints"
  val lower_bound_token: String = "lower_bound"
  val upper_bound_token: String = "upper_bound"
  val inputs_token: String = "inputs"
  val outputs_token: String = "outputs"
  val implementation_token: String = "implementation"
  val operations_token: String = "operations"
  val valid_sources_token: String = "valid_sources"
  val valid_targets_token: String = "valid_targets"
  val file_ext_token: String = "file_ext"
  val relationship_type_token: String = "relationship_type"
  val topology_template_token: String = "topology_template"
  val node_templates_token: String = "node_templates"
  val node_token: String = "node"
  val capability_token: String = "capability"
  val value_token: String = "value"
  val get_input_token: String = "get_input"
  val get_property_token: String = "get_property"
  val get_attribute_token: String = "get_attribute"
  val get_operation_output_token: String = "get_operation_output"
  val concat_token = "concat"

  def fileExtension = wrapParserWithPosition( """[\p{Print} && [^:\[\]\{\}>-]]*""".r ^^ (_.toString))

  def artifactTypeEntry(indentLevel: Int = 0) =
    textEntry(derived_from_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      nestedListEntry(file_ext_token)(fileExtension)

  def artifactType(artifactTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(artifactTypeEntry)(indentLevel) ^^ {
      case map => ArtifactType(
        artifactTypeName,
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[List[ParsedValue[String]]](map, file_ext_token))
    })

  def artifactTypesEntry(indentLevel: Int = 0) = (keyValue into (artifactTypeName => keyComplexSeparatorPattern ~> artifactType(artifactTypeName)(indentLevel + 1))) ^^ {
    case artifactType => (artifactType.name, artifactType)
  }

  def relationshipTypeEntry(indentLevel: Int) =
    textEntry(derived_from_token)(indentLevel) |
      booleanEntry(abstract_token) |
      textEntry(description_token)(indentLevel) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel) |
      mapEntry(attributes_token)(attributeDefinitionsEntry)(indentLevel) |
      nestedListEntry(valid_sources_token)(nestedTextValue) |
      nestedListEntry(valid_targets_token)(nestedTextValue) |
      listEntry(artifacts_token)(textEntry(keyValue))(indentLevel) |
      mapEntry(interfaces_token)(interfaceDefinitionsEntry)(indentLevel)

  def relationshipType(relationshipTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(relationshipTypeEntry)(indentLevel) ^^ {
      case map => RelationshipType(
        relationshipTypeName,
        get[ParsedValue[Boolean]](map, abstract_token).getOrElse(ParsedValue(false)),
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token),
        get[Map[ParsedValue[String], FieldValue]](map, attributes_token),
        get[List[ParsedValue[String]]](map, valid_sources_token),
        get[List[ParsedValue[String]]](map, valid_targets_token),
        get[Map[ParsedValue[String], ParsedValue[String]]](map, artifacts_token),
        get[Map[ParsedValue[String], Interface]](map, interfaces_token))
    })

  def relationshipTypesEntry(indentLevel: Int) = (keyValue into (relationshipTypeName => keyComplexSeparatorPattern ~> relationshipType(relationshipTypeName)(indentLevel + 1))) ^^ {
    case relationshipType => (relationshipType.name, relationshipType)
  }

  def capabilityTypeEntry(indentLevel: Int) =
    textEntry(derived_from_token)(indentLevel) |
      booleanEntry(abstract_token) |
      textEntry(description_token)(indentLevel) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel)

  def capabilityType(capabilityTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(capabilityTypeEntry)(indentLevel) ^^ {
      case map => CapabilityType(
        capabilityTypeName,
        get[ParsedValue[Boolean]](map, abstract_token).getOrElse(ParsedValue(false)),
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token))
    })

  def capabilityTypesEntry(indentLevel: Int) = (keyValue into (capabilityTypeName => keyComplexSeparatorPattern ~> capabilityType(capabilityTypeName)(indentLevel + 1))) ^^ {
    case capabilityType => (capabilityType.name, capabilityType)
  }

  def constraintSingleValueOperator = wrapParserWithPosition("equal" | "greater_than" | "greater_or_equal" | "less_than" | "less_or_equal" | "length" | "min_length" | "max_length" | "pattern")

  def constraintMultipleValueOperator = wrapParserWithPosition("valid_values" | "in_range")

  def constraintEntry(indentLevel: Int) =
    positioned(
      (textEntry(constraintSingleValueOperator)(indentLevel) |
        nestedListEntry(constraintMultipleValueOperator)(nestedTextValue)) ^^ {
        case (operator, reference) => PropertyConstraint(operator, reference)
      })

  def propertyDefinitionEntry(indentLevel: Int) =
    textEntry(type_token)(indentLevel) |
      booleanEntry(required_token) |
      textEntry(default_token)(indentLevel) |
      listEntry(constraints_token)(constraintEntry)(indentLevel) |
      textEntry(description_token)(indentLevel)


  def propertyDefinition(indentLevel: Int): Parser[PropertyDefinition] = positioned(
    map(propertyDefinitionEntry)(indentLevel) ^^ {
      case map => PropertyDefinition(
        get[ParsedValue[String]](map, type_token).getOrElse(ParsedValue(FieldDefinition.STRING)),
        get[ParsedValue[Boolean]](map, required_token).getOrElse(ParsedValue(true)),
        get[ParsedValue[String]](map, default_token),
        get[List[PropertyConstraint]](map, constraints_token),
        get[ParsedValue[String]](map, description_token)
      )
    })

  def propertyDefinitionsEntry(indentLevel: Int) = (keyValue ~ (keyComplexSeparatorPattern ~> propertyDefinition(indentLevel))) ^^ entryParseResultHandler

  def attributeDefinitionEntry(indentLevel: Int) =
    textEntry(type_token)(indentLevel) |
      textEntry(default_token)(indentLevel) |
      textEntry(description_token)(indentLevel)

  def attributeDefinition(indentLevel: Int): Parser[AttributeDefinition] = positioned(
    map(attributeDefinitionEntry)(indentLevel) ^^ {
      case map => AttributeDefinition(
        get[ParsedValue[String]](map, type_token).getOrElse(ParsedValue(FieldDefinition.STRING)),
        get[ParsedValue[String]](map, description_token),
        get[ParsedValue[String]](map, default_token)
      )
    })

  def attributeDefinitionsEntry(indentLevel: Int) =
    (keyValue ~ (keyComplexSeparatorPattern ~> attributeDefinition(indentLevel))) ^^ entryParseResultHandler | nestedComplexEntry(keyValue)(function)

  def requirementDefinitionEntry(indentLevel: Int) =
    textEntry(type_token)(indentLevel) |
      textEntry(relationship_type_token)(indentLevel) |
      intEntry(lower_bound_token) |
      intEntry(upper_bound_token)

  def requirementDefinition(indentLevel: Int): Parser[RequirementDefinition] = positioned(
    map(requirementDefinitionEntry)(indentLevel) ^^ {
      case map => RequirementDefinition(
        get[ParsedValue[String]](map, type_token),
        get[ParsedValue[String]](map, relationship_type_token),
        get[ParsedValue[Int]](map, lower_bound_token).getOrElse(ParsedValue(1)),
        get[ParsedValue[Int]](map, upper_bound_token).getOrElse(ParsedValue(1))
      )
    })

  def requirementDefinitionsEntry(indentLevel: Int) =
    (keyValue ~ (keyComplexSeparatorPattern ~> requirementDefinition(indentLevel))) ^^ entryParseResultHandler

  def capabilityDefinitionEntry(indentLevel: Int) =
    textEntry(type_token)(indentLevel) |
      intEntry(upper_bound_token) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel)

  def simpleCapabilityDefinition(indentLevel: Int) = positioned(textValue ^^ {
    case capabilityType => CapabilityDefinition(Some(capabilityType), ParsedValue(1), None)
  })

  def capabilityDefinition(indentLevel: Int): Parser[CapabilityDefinition] = positioned(
    map(capabilityDefinitionEntry)(indentLevel) ^^ {
      case map => CapabilityDefinition(
        get[ParsedValue[String]](map, type_token),
        get[ParsedValue[Int]](map, upper_bound_token).getOrElse(ParsedValue(1)),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token)
      )
    })

  def capabilityDefinitionsEntry(indentLevel: Int) =
    (keyValue ~ ((keyComplexSeparatorPattern ~> capabilityDefinition(indentLevel)) | (keyValueSeparatorPattern ~> simpleCapabilityDefinition(indentLevel) <~ lineEndingPattern))) ^^ entryParseResultHandler

  def simpleFunctionName = wrapParserWithPosition(get_property_token | get_attribute_token | get_operation_output_token)

  def compositeFunctionName = wrapParserWithPosition(concat_token)

  def multipleArgumentsFunction = positioned(internalNestedListEntry(simpleFunctionName)(nestedTextValue) ^^ {
    case (functionName, paths: Seq[ParsedValue[String]]) => Function(functionName, paths)
  })

  def singleArgumentFunction = positioned(nestedTextEntry(get_input_token) ^^ { case (functionName, path) => Function(functionName, Seq(path)) })

  def compositeFunction = positioned(internalNestedListEntry(compositeFunctionName)(singleArgumentFunction | multipleArgumentsFunction | nestedTextValue ^^ { case textValue => ScalarValue(textValue) }) ^^ {
    case (functionName, members: Seq[FieldValue]) =>
      CompositeFunction(functionName, members)
  })

  def function = compositeFunction | singleArgumentFunction | multipleArgumentsFunction

  def scalarTextEntry(indentLevel: Int) = textEntry(keyValue)(indentLevel) ^^ { case (key: ParsedValue[String], value: ParsedValue[String]) => (key, ScalarValue(value)) }

  def operationInputEntry(indentLevel: Int) =
    scalarTextEntry(indentLevel) |
      nestedComplexEntry(keyValue)(function) |
      propertyDefinitionsEntry(indentLevel)

  def operationEntry(indentLevel: Int) =
    textEntry(description_token)(indentLevel) |
      mapEntry(inputs_token)(operationInputEntry)(indentLevel) |
      textEntry(implementation_token)(indentLevel)

  def operation(indentLevel: Int) = positioned(
    map(operationEntry)(indentLevel) ^^ {
      case map => Operation(
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], FieldValue]](map, inputs_token),
        get[ParsedValue[String]](map, implementation_token))
    })

  def simpleOperation = positioned(textValue ^^ {
    case implementation => Operation(None, None, if (implementation.value == null || implementation.value.isEmpty) None else Some(implementation))
  })

  def operationsEntry(indentLevel: Int) =
    complexEntry(keyValue)(operation)(indentLevel) | (keyValue ~ (keyValueSeparatorPattern ~> simpleOperation <~ lineEndingPattern)) ^^ entryParseResultHandler

  def interfaceDefinitionEntry(indentLevel: Int) =
    textEntry(description_token)(indentLevel) |
      (internalMap(operationsEntry)(indentLevel) ^^ {
        case operations => (ParsedValue(operations_token), operations)
      })

  def interfaceDefinition(indentLevel: Int) = positioned(
    map(interfaceDefinitionEntry)(indentLevel) ^^ {
      case map => Interface(
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], Operation]](map, operations_token).getOrElse(Map.empty)
      )
    })

  def interfaceDefinitionsEntry(indentLevel: Int) =
    keyValue ~ (keyComplexSeparatorPattern ~> interfaceDefinition(indentLevel)) ^^ entryParseResultHandler

  def nodeTypeEntry(indentLevel: Int) =
    booleanEntry(abstract_token) |
      textEntry(derived_from_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      mapEntry(tags_token)(textEntry(keyValue))(indentLevel) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel) |
      mapEntry(attributes_token)(attributeDefinitionsEntry)(indentLevel) |
      mapEntry(requirements_token)(requirementDefinitionsEntry)(indentLevel) |
      mapEntry(capabilities_token)(capabilityDefinitionsEntry)(indentLevel) |
      listEntry(artifacts_token)(textEntry(keyValue))(indentLevel) |
      mapEntry(interfaces_token)(interfaceDefinitionsEntry)(indentLevel)

  def nodeType(nodeTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(nodeTypeEntry)(indentLevel) ^^ {
      case map => NodeType(
        nodeTypeName,
        get[ParsedValue[Boolean]](map, abstract_token).getOrElse(ParsedValue(false)),
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], ParsedValue[String]]](map, tags_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token),
        get[Map[ParsedValue[String], FieldValue]](map, attributes_token),
        get[Map[ParsedValue[String], RequirementDefinition]](map, requirements_token),
        get[Map[ParsedValue[String], CapabilityDefinition]](map, capabilities_token),
        get[Map[ParsedValue[String], ParsedValue[String]]](map, artifacts_token),
        get[Map[ParsedValue[String], Interface]](map, interfaces_token))
    })

  def nodeTypesEntry(indentLevel: Int) = (keyValue into (nodeTypeName => keyComplexSeparatorPattern ~> nodeType(nodeTypeName)(indentLevel + 1))) ^^ {
    case nodeType => (nodeType.name, nodeType)
  }

  def properties(indentLevel: Int) = map(operationInputEntry)(indentLevel)

  def requirementEntry(indentLevel: Int) =
    textEntry(node_token)(indentLevel) |
      textEntry(capability_token)(indentLevel)

  def requirement(requirementName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(requirementEntry)(indentLevel) ^^ {
      case map => Requirement(
        requirementName,
        get[ParsedValue[String]](map, node_token),
        get[ParsedValue[String]](map, capability_token))
    })

  def simpleRequirement(requirementName: ParsedValue[String])(indentLevel: Int) = positioned(textValue ^^ {
    case targetNode => Requirement(requirementName, Some(targetNode), None)
  })

  def requirementsEntry(indentLevel: Int) =
    keyValue into (requirementName => (keyComplexSeparatorPattern ~> requirement(requirementName)(indentLevel + 1)) | (keyValueSeparatorPattern ~> simpleRequirement(requirementName)(indentLevel) <~ lineEndingPattern))

  def requirements(indentLevel: Int) = list(requirementsEntry)(indentLevel)

  def nodeTemplateEntry(indentLevel: Int) =
    textEntry(type_token)(indentLevel) |
      complexEntry(properties_token)(properties)(indentLevel) |
      complexEntry(requirements_token)(requirements)(indentLevel)

  def nodeTemplate(nodeTemplateName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(nodeTemplateEntry)(indentLevel) ^^ {
      case map => NodeTemplate(
        nodeTemplateName,
        get[ParsedValue[String]](map, type_token),
        get[Map[ParsedValue[String], FieldValue]](map, properties_token),
        get[List[Requirement]](map, requirements_token))
    })

  def nodeTemplatesEntry(indentLevel: Int) = (keyValue into (nodeTemplateName => keyComplexSeparatorPattern ~> nodeTemplate(nodeTemplateName)(indentLevel + 1))) ^^ {
    case nodeTemplate => (nodeTemplate.name, nodeTemplate)
  }

  def outputValueEntry(indentLevel: Int) =
    textEntry(value_token)(indentLevel) | nestedComplexEntry(value_token)(function)

  def outputEntry(indentLevel: Int) =
    textEntry(description_token)(indentLevel) |
      outputValueEntry(indentLevel)

  def output(outputName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(outputEntry)(indentLevel) ^^ {
      case map => Output(
        outputName,
        get[ParsedValue[String]](map, description_token),
        get[FieldValue](map, value_token)
      )
    })

  def outputsEntry(indentLevel: Int) = (keyValue into (outputName => keyComplexSeparatorPattern ~> output(outputName)(indentLevel + 1))) ^^ {
    case output => (output.name, output)
  }

  def topologyTemplateEntry(indentLevel: Int) =
    textEntry(description_token)(indentLevel) |
      mapEntry(inputs_token)(propertyDefinitionsEntry)(indentLevel) |
      mapEntry(outputs_token)(outputsEntry)(indentLevel) |
      mapEntry(node_templates_token)(nodeTemplatesEntry)(indentLevel)

  def topologyTemplate(indentLevel: Int) =
    positioned(map(topologyTemplateEntry)(indentLevel) ^^ {
      case map => TopologyTemplate(
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, inputs_token),
        get[Map[ParsedValue[String], Output]](map, outputs_token),
        get[Map[ParsedValue[String], NodeTemplate]](map, node_templates_token))
    })

  def definitionEntry(indentLevel: Int) =
    textEntry(definitions_version_token)(indentLevel) |
      textEntry(template_name_token)(indentLevel) |
      textEntry(template_version_token)(indentLevel) |
      textEntry(template_author_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      mapEntry(node_types_token)(nodeTypesEntry)(indentLevel) |
      mapEntry(capability_types_token)(capabilityTypesEntry)(indentLevel) |
      mapEntry(relationship_types_token)(relationshipTypesEntry)(indentLevel) |
      mapEntry(artifact_types_token)(artifactTypesEntry)(indentLevel) |
      complexEntry(topology_template_token)(topologyTemplate)(indentLevel)

  def definition =
    positioned(phrase(opt(lineEndingPattern) ~> map(definitionEntry)(0) <~ opt(lineEndingPattern)) ^^ {
      case map => Definition(
        get[ParsedValue[String]](map, definitions_version_token),
        get[ParsedValue[String]](map, template_name_token),
        get[ParsedValue[String]](map, template_version_token),
        get[ParsedValue[String]](map, template_author_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], NodeType]](map, node_types_token),
        get[Map[ParsedValue[String], CapabilityType]](map, capability_types_token),
        get[Map[ParsedValue[String], RelationshipType]](map, relationship_types_token),
        get[Map[ParsedValue[String], ArtifactType]](map, artifact_types_token),
        get[TopologyTemplate](map, topology_template_token))
    })

  def get[T](map: Map[ParsedValue[String], Any], key: String): Option[T] = map.get(ParsedValue(key)).asInstanceOf[Option[T]]
}
