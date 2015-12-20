package com.toscaruntime.compiler

import com.toscaruntime.compiler.Tokens._
import com.toscaruntime.compiler.parser.YamlParser
import com.toscaruntime.compiler.tosca._

object SyntaxAnalyzer extends YamlParser {

  def fileExtension = wrapParserWithPosition( """[\p{Print} && [^:\[\]\{\}>-]]*""".r ^^ (_.toString))

  def artifactTypeEntry(indentLevel: Int = 0) =
    (textEntry(derived_from_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      textEntry(mime_type_token)(indentLevel) |
      nestedListEntry(file_ext_token)(fileExtension)) | failure(s"Expecting one of '$derived_from_token', '$description_token', '$mime_type_token', '$file_ext_token'")

  def artifactType(artifactTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(artifactTypeEntry)(indentLevel) ^^ {
      case map => ArtifactType(
        artifactTypeName,
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[List[ParsedValue[String]]](map, file_ext_token))
    })

  def artifactTypesEntry(indentLevel: Int = 0) =
    (keyValue into (artifactTypeName => keyComplexSeparatorPattern ~> artifactType(artifactTypeName)(indentLevel + 1))) ^^ {
      case artifactType => (artifactType.name, artifactType)
    }

  def relationshipTypeEntry(indentLevel: Int) =
    (textEntry(derived_from_token)(indentLevel) |
      booleanEntry(abstract_token) |
      textEntry(description_token)(indentLevel) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel) |
      mapEntry(attributes_token)(attributeDefinitionsEntry)(indentLevel) |
      nestedListEntry(valid_sources_token)(nestedTextValue) |
      nestedListEntry(valid_targets_token)(nestedTextValue) |
      listEntry(artifacts_token)(textEntry(keyValue))(indentLevel) |
      mapEntry(interfaces_token)(interfaceDefinitionsEntry)(indentLevel)
      ) | failure(s"Expecting one of '$derived_from_token', '$abstract_token', '$description_token', '$properties_token', '$attributes_token', '$valid_sources_token', '$valid_targets_token', '$artifacts_token', '$interfaces_token'")

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

  def relationshipTypesEntry(indentLevel: Int) =
    (keyValue into (relationshipTypeName => keyComplexSeparatorPattern ~> relationshipType(relationshipTypeName)(indentLevel + 1))) ^^ {
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

  def capabilityTypesEntry(indentLevel: Int) =
    (keyValue into (capabilityTypeName => keyComplexSeparatorPattern ~> capabilityType(capabilityTypeName)(indentLevel + 1))) ^^ {
      case capabilityType => (capabilityType.name, capabilityType)
    }

  def constraintSingleValueOperator =
    wrapParserWithPosition(
      equal_token |
        greater_than_token |
        greater_or_equal_token |
        less_than_token |
        less_or_equal_token |
        length_token |
        min_length_token |
        max_length_token |
        pattern_token
    ) | failure(s"Expecting one of '$equal_token', '$greater_than_token', '$greater_or_equal_token', '$less_than_token', '$less_or_equal_token', '$length_token', '$min_length_token', '$max_length_token', '$pattern_token'")

  def constraintMultipleValueOperator = wrapParserWithPosition(valid_values_token | in_range_token) | failure(s"Expecting one of '$valid_values_token', '$in_range_token'")

  def constraintEntry(indentLevel: Int) =
    positioned(
      (textEntry(constraintSingleValueOperator)(indentLevel) |
        nestedListEntry(constraintMultipleValueOperator)(nestedTextValue)) ^^ {
        case (operator, reference) => PropertyConstraint(operator, reference)
      }
    )

  def propertyDefinitionEntry(indentLevel: Int) =
    (textEntry(type_token)(indentLevel) |
      booleanEntry(required_token) |
      textEntry(default_token)(indentLevel) |
      listEntry(constraints_token)(constraintEntry)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      complexEntry(entry_schema_token)(propertyDefinition)(indentLevel)) | failure(s"Expecting one of '$type_token', '$required_token', '$default_token', '$constraints_token', '$description_token', '$entry_schema_token'")


  def propertyDefinition(indentLevel: Int): Parser[PropertyDefinition] = positioned(
    map(propertyDefinitionEntry)(indentLevel) ^^ {
      case map => PropertyDefinition(
        get[ParsedValue[String]](map, type_token).getOrElse(ParsedValue(FieldDefinition.STRING)),
        get[ParsedValue[Boolean]](map, required_token).getOrElse(ParsedValue(true)),
        get[ParsedValue[String]](map, default_token),
        get[List[PropertyConstraint]](map, constraints_token),
        get[ParsedValue[String]](map, description_token),
        get[PropertyDefinition](map, entry_schema_token)
      )
    })

  def propertyDefinitionsEntry(indentLevel: Int) =
    (keyValue ~ (keyComplexSeparatorPattern ~> propertyDefinition(indentLevel))) ^^ entryParseResultHandler | nestedComplexEntry(keyValue)(function)

  def attributeDefinitionEntry(indentLevel: Int) =
    (textEntry(type_token)(indentLevel) |
      textEntry(default_token)(indentLevel) |
      textEntry(description_token)(indentLevel)) | failure(s"Expecting one of '$type_token', '$default_token', '$description_token'")

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
    (textEntry(type_token)(indentLevel) |
      textEntry(relationship_token)(indentLevel) |
      intEntry(lower_bound_token) |
      intEntry(upper_bound_token) |
      textEntry(description_token)(indentLevel)) | failure(s"Expecting one of '$type_token', '$relationship_token', '$lower_bound_token', '$upper_bound_token'")

  def requirementDefinitionList(name: ParsedValue[String], capabilityType: ParsedValue[String], indentLevel: Int): Parser[RequirementDefinition] = positioned(
    map(requirementDefinitionEntry)(indentLevel) ^^ {
      case map => RequirementDefinition(
        Some(name),
        Some(capabilityType),
        get[ParsedValue[String]](map, relationship_token),
        get[ParsedValue[Int]](map, lower_bound_token).getOrElse(ParsedValue(1)),
        get[ParsedValue[Int]](map, upper_bound_token).getOrElse(ParsedValue(1)),
        get[ParsedValue[String]](map, description_token)
      )
    })

  def requirementDefinition(name: ParsedValue[String], indentLevel: Int): Parser[RequirementDefinition] = positioned(
    map(requirementDefinitionEntry)(indentLevel) ^^ {
      case map => RequirementDefinition(
        Some(name),
        get[ParsedValue[String]](map, type_token),
        get[ParsedValue[String]](map, relationship_token),
        get[ParsedValue[Int]](map, lower_bound_token).getOrElse(ParsedValue(1)),
        get[ParsedValue[Int]](map, upper_bound_token).getOrElse(ParsedValue(1)),
        get[ParsedValue[String]](map, description_token)
      )
    })

  def requirementDefinitionsEntry(indentLevel: Int) =
    keyValue into (name => keyComplexSeparatorPattern ~> requirementDefinition(name, indentLevel) ^^ {
      case requirement => (name, requirement)
    })

  def requirementDefinitionsListEntry(indentLevel: Int) =
    textEntry(keyValue)(indentLevel) into {
      case (requirementName, capabilityType) => requirementDefinitionList(requirementName, capabilityType, indentLevel)
    }

  def capabilityDefinitionEntry(indentLevel: Int) =
    (textEntry(type_token)(indentLevel) |
      intEntry(upper_bound_token) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel) |
      textEntry(description_token)(indentLevel)) | failure(s"Expecting one of '$type_token', '$upper_bound_token', '$properties_token'")

  def simpleCapabilityDefinition(indentLevel: Int) = positioned(textValue ^^ {
    case capabilityType => CapabilityDefinition(Some(capabilityType), ParsedValue(1), None, None)
  })

  def capabilityDefinition(indentLevel: Int): Parser[CapabilityDefinition] = positioned(
    map(capabilityDefinitionEntry)(indentLevel) ^^ {
      case map => CapabilityDefinition(
        get[ParsedValue[String]](map, type_token),
        get[ParsedValue[Int]](map, upper_bound_token).getOrElse(ParsedValue(1)),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token),
        get[ParsedValue[String]](map, description_token)
      )
    })

  def capabilityDefinitionsEntry(indentLevel: Int) =
    (keyValue ~ ((keyComplexSeparatorPattern ~> capabilityDefinition(indentLevel)) | (keyValueSeparatorPattern ~> simpleCapabilityDefinition(indentLevel) <~ lineEndingPattern))) ^^ entryParseResultHandler

  def simpleFunctionName = wrapParserWithPosition(get_property_token | get_attribute_token | get_operation_output_token) | failure(s"Expecting one of '$get_property_token', '$get_attribute_token', '$get_operation_output_token'")

  def compositeFunctionName = wrapParserWithPosition(concat_token) | failure(s"Expecting one of '$concat_token'")

  def multipleArgumentsFunction = positioned(internalNestedListEntry(simpleFunctionName)(nestedTextValue) ^^ {
    case (functionName, paths: Seq[ParsedValue[String]]) => Function(functionName, paths)
  })

  def singleArgumentFunction = positioned(nestedTextEntry(get_input_token) ^^ { case (functionName, path) => Function(functionName, Seq(path)) })

  def compositeFunction =
    positioned(internalNestedListEntry(compositeFunctionName)(singleArgumentFunction | multipleArgumentsFunction | nestedTextValue ^^ { case textValue => ScalarValue(textValue) }) ^^ {
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
      textEntry(implementation_token)(indentLevel) | failure(s"Expecting one of '$description_token', '$inputs_token', '$implementation_token'")

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
    (booleanEntry(abstract_token) |
      textEntry(derived_from_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      mapEntry(tags_token)(textEntry(keyValue))(indentLevel) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel) |
      mapEntry(attributes_token)(attributeDefinitionsEntry)(indentLevel) |
      (listEntry(requirements_token)(requirementDefinitionsListEntry)(indentLevel) ^^ {
        case (requirementsToken, requirements) => (requirementsToken, requirements.map {
          case requirement => (requirement.name, requirement)
        }.toMap)
      }) |
      mapEntry(requirements_token)(requirementDefinitionsEntry)(indentLevel) |
      mapEntry(capabilities_token)(capabilityDefinitionsEntry)(indentLevel) |
      listEntry(artifacts_token)(textEntry(keyValue))(indentLevel) |
      mapEntry(interfaces_token)(interfaceDefinitionsEntry)(indentLevel)
      ) | failure(s"Expecting one of '$abstract_token', '$derived_from_token', '$description_token', '$tags_token', '$properties_token', '$attributes_token', '$requirements_token', '$capabilities_token', '$artifacts_token', '$interfaces_token'")

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

  def nodeTypesEntry(indentLevel: Int) =
    (keyValue into (nodeTypeName => keyComplexSeparatorPattern ~> nodeType(nodeTypeName)(indentLevel + 1))) ^^ {
      case nodeType => (nodeType.name, nodeType)
    }

  def dataTypesEntry(indentLevel: Int) =
    (keyValue into (dataTypeName => keyComplexSeparatorPattern ~> dataType(dataTypeName)(indentLevel + 1))) ^^ {
      case dataType => (dataType.name, dataType)
    }

  def policyTypesEntry(indentLevel: Int) =
    (keyValue into (polictyTypeName => keyComplexSeparatorPattern ~> policyType(polictyTypeName)(indentLevel + 1))) ^^ {
      case polictyType => (polictyType.name, polictyType)
    }

  def groupTypesEntry(indentLevel: Int) =
    (keyValue into (groupTypeName => keyComplexSeparatorPattern ~> groupType(groupTypeName)(indentLevel + 1))) ^^ {
      case groupType => (groupType.name, groupType)
    }

  def groupTypeEntry(indentLevel: Int) =
    (textEntry(derived_from_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      mapEntry(interfaces_token)(interfaceDefinitionsEntry)(indentLevel)
      ) | failure(s"Expecting one of '$derived_from_token', '$description_token', '$interfaces_token'")

  def policyTypeEntry(indentLevel: Int) =
    (textEntry(derived_from_token)(indentLevel) |
      textEntry(description_token)(indentLevel)) | failure(s"Expecting one of '$derived_from_token', '$description_token'")


  def dataTypeEntry(indentLevel: Int) =
    (booleanEntry(abstract_token) |
      textEntry(derived_from_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      mapEntry(properties_token)(propertyDefinitionsEntry)(indentLevel)
      ) | failure(s"Expecting one of '$abstract_token', '$derived_from_token', '$description_token', '$properties_token'")

  def groupType(groupTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(groupTypeEntry)(indentLevel) ^^ {
      case map => GroupType(
        groupTypeName,
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], Interface]](map, interfaces_token))
    })

  def policyType(policyTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(policyTypeEntry)(indentLevel) ^^ {
      case map => PolicyType(
        policyTypeName,
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token))
    })

  def dataType(dataTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(dataTypeEntry)(indentLevel) ^^ {
      case map => DataType(
        dataTypeName,
        get[ParsedValue[Boolean]](map, abstract_token).getOrElse(ParsedValue(false)),
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token))
    })

  def properties(indentLevel: Int) = map(operationInputEntry)(indentLevel)

  def requirementEntry(indentLevel: Int) =
    (textEntry(node_token)(indentLevel) |
      textEntry(capability_token)(indentLevel)) | failure(s"Expecting one of '$node_token', '$capability_token'")

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
    keyValue into (
      requirementName => (keyComplexSeparatorPattern ~> requirement(requirementName)(indentLevel + 1)) |
        (keyValueSeparatorPattern ~> simpleRequirement(requirementName)(indentLevel) <~ lineEndingPattern)
      )

  def requirements(indentLevel: Int) = list(requirementsEntry)(indentLevel)

  def nodeTemplateEntry(indentLevel: Int) =
    (textEntry(type_token)(indentLevel) |
      complexEntry(properties_token)(properties)(indentLevel) |
      complexEntry(requirements_token)(requirements)(indentLevel)) | failure(s"Expecting one of '$type_token', '$properties_token', '$requirements_token'")

  def nodeTemplate(nodeTemplateName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(nodeTemplateEntry)(indentLevel) ^^ {
      case map => NodeTemplate(
        nodeTemplateName,
        get[ParsedValue[String]](map, type_token),
        get[Map[ParsedValue[String], FieldValue]](map, properties_token),
        get[List[Requirement]](map, requirements_token))
    })

  def nodeTemplatesEntry(indentLevel: Int) =
    (keyValue into (nodeTemplateName => keyComplexSeparatorPattern ~> nodeTemplate(nodeTemplateName)(indentLevel + 1))) ^^ {
      case nodeTemplate => (nodeTemplate.name, nodeTemplate)
    }

  def outputValueEntry(indentLevel: Int) =
    (textEntry(value_token)(indentLevel) |
      nestedComplexEntry(value_token)(function)) | failure(s"Expecting one of '$value_token'")

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

  def outputsEntry(indentLevel: Int) =
    (keyValue into (outputName => keyComplexSeparatorPattern ~> output(outputName)(indentLevel + 1))) ^^ {
      case output => (output.name, output)
    }

  def topologyTemplateEntry(indentLevel: Int) =
    (textEntry(description_token)(indentLevel) |
      mapEntry(inputs_token)(propertyDefinitionsEntry)(indentLevel) |
      mapEntry(outputs_token)(outputsEntry)(indentLevel) |
      mapEntry(node_templates_token)(nodeTemplatesEntry)(indentLevel)) | failure(s"Expecting one of '$description_token', '$inputs_token', '$outputs_token', '$node_templates_token'")

  def topologyTemplate(indentLevel: Int) =
    positioned(map(topologyTemplateEntry)(indentLevel) ^^ {
      case map => TopologyTemplate(
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, inputs_token),
        get[Map[ParsedValue[String], Output]](map, outputs_token),
        get[Map[ParsedValue[String], NodeTemplate]](map, node_templates_token))
    })

  def definitionEntry(indentLevel: Int) =
    (textEntry(definitions_version_token)(indentLevel) |
      textEntry(template_name_token)(indentLevel) |
      textEntry(template_version_token)(indentLevel) |
      textEntry(template_author_token)(indentLevel) |
      textEntry(description_token)(indentLevel) |
      listEntry(imports_token)(indentLevel => textValue)(indentLevel) |
      mapEntry(node_types_token)(nodeTypesEntry)(indentLevel) |
      mapEntry(data_types_token)(dataTypesEntry)(indentLevel) |
      mapEntry(group_types_token)(groupTypesEntry)(indentLevel) |
      mapEntry(policy_types_token)(policyTypesEntry)(indentLevel) |
      mapEntry(capability_types_token)(capabilityTypesEntry)(indentLevel) |
      mapEntry(relationship_types_token)(relationshipTypesEntry)(indentLevel) |
      mapEntry(artifact_types_token)(artifactTypesEntry)(indentLevel) |
      complexEntry(topology_template_token)(topologyTemplate)(indentLevel)
      ) | failure(s"Expecting one of '$definitions_version_token', '$template_name_token', '$template_version_token', '$template_author_token', '$description_token', '$imports_token', '$node_types_token', '$data_types_token', '$capability_types_token', '$relationship_types_token', '$artifact_types_token', '$topology_template_token'")

  def definition =
    positioned(phrase(opt(lineEndingPattern) ~> map(definitionEntry)(0) <~ opt(lineEndingPattern)) ^^ {
      case map => Definition(
        get[ParsedValue[String]](map, definitions_version_token),
        get[ParsedValue[String]](map, template_name_token),
        get[ParsedValue[String]](map, template_version_token),
        get[List[ParsedValue[String]]](map, imports_token),
        get[ParsedValue[String]](map, template_author_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], NodeType]](map, node_types_token),
        get[Map[ParsedValue[String], DataType]](map, data_types_token),
        get[Map[ParsedValue[String], CapabilityType]](map, capability_types_token),
        get[Map[ParsedValue[String], RelationshipType]](map, relationship_types_token),
        get[Map[ParsedValue[String], ArtifactType]](map, artifact_types_token),
        get[Map[ParsedValue[String], GroupType]](map, group_types_token),
        get[Map[ParsedValue[String], PolicyType]](map, policy_types_token),
        get[TopologyTemplate](map, topology_template_token))
    })

  def get[T](map: Map[ParsedValue[String], Any], key: String): Option[T] = map.get(ParsedValue(key)).asInstanceOf[Option[T]]
}
