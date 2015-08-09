package com.mkv.tosca.compiler

import com.mkv.tosca.compiler.tosca._
import com.mkv.tosca.compiler.parser.YamlParser

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

  def fileExtension = """[\p{Print} && [^:\[\]\{\}>-]]*""".r ^^ (_.toString)

  def artifactTypeEntry(indentLevel: Int = 0) =
    textEntry(indentLevel)(derived_from_token) |
      textEntry(indentLevel)(description_token) |
      ((wrapTextWithPosition(file_ext_token) <~ keyValueSeparatorPattern <~ """\[ *""".r) ~ (rep1sep(wrapParserWithPosition(fileExtension), """ *, *""") <~ """ *\]""".r <~ newLinePattern)) ^^ entryParseResultHandler

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
    textEntry(indentLevel)(derived_from_token) |
      booleanEntry(indentLevel)(abstract_token) |
      textEntry(indentLevel)(description_token) |
      mapEntry(indentLevel)(properties_token)(propertyDefinitionsEntry) |
      (((wrapTextWithPosition(valid_sources_token) <~ keyValueSeparatorPattern <~ """\[ *""".r) ~ (rep1sep(nestedTextValue, """ *, *""") <~ """ *\]""".r <~ newLinePattern)) ^^ entryParseResultHandler) |
      (((wrapTextWithPosition(valid_targets_token) <~ keyValueSeparatorPattern <~ """\[ *""".r) ~ (rep1sep(nestedTextValue, """ *, *""") <~ """ *\]""".r <~ newLinePattern)) ^^ entryParseResultHandler) |
      listEntry(indentLevel)(artifacts_token)(genericTextEntry) |
      mapEntry(indentLevel)(interfaces_token)(interfaceDefinitionsEntry)

  def relationshipType(relationshipTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(relationshipTypeEntry)(indentLevel) ^^ {
      case map => RelationshipType(
        relationshipTypeName,
        get[ParsedValue[Boolean]](map, abstract_token).getOrElse(ParsedValue(false)),
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token),
        get[List[ParsedValue[String]]](map, valid_sources_token),
        get[List[ParsedValue[String]]](map, valid_targets_token),
        get[Map[ParsedValue[String], ParsedValue[String]]](map, artifacts_token),
        get[Map[ParsedValue[String], Interface]](map, interfaces_token))
    })

  def relationshipTypesEntry(indentLevel: Int) = (keyValue into (relationshipTypeName => keyComplexSeparatorPattern ~> relationshipType(relationshipTypeName)(indentLevel + 1))) ^^ {
    case relationshipType => (relationshipType.name, relationshipType)
  }

  def capabilityTypeEntry(indentLevel: Int) =
    textEntry(indentLevel)(derived_from_token) |
      booleanEntry(indentLevel)(abstract_token) |
      textEntry(indentLevel)(description_token) |
      mapEntry(indentLevel)(properties_token)(propertyDefinitionsEntry)

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
      ((constraintSingleValueOperator ~ (keyValueSeparatorPattern ~> textValue <~ newLinePattern)) |
        (constraintMultipleValueOperator ~ (keyValueSeparatorPattern ~> """\[ *""".r ~> rep1sep(nestedTextValue, """ *, *""".r) <~ """ *\]""".r <~ newLinePattern))) ^^ {
        case (operator ~ reference) => PropertyConstraint(operator, reference)
      })

  def propertyDefinitionEntry(indentLevel: Int) =
    textEntry(indentLevel)(type_token) |
      booleanEntry(indentLevel)(required_token) |
      textEntry(indentLevel)(default_token) |
      listEntry(indentLevel)(constraints_token)(constraintEntry) |
      textEntry(indentLevel)(description_token)


  def propertyDefinition(indentLevel: Int): Parser[PropertyDefinition] = positioned(
    map(propertyDefinitionEntry)(indentLevel) ^^ {
      case map => PropertyDefinition(
        get[ParsedValue[String]](map, type_token).getOrElse(ParsedValue(Field.STRING)),
        get[ParsedValue[Boolean]](map, required_token).getOrElse(ParsedValue(true)),
        get[ParsedValue[String]](map, default_token),
        get[List[PropertyConstraint]](map, constraints_token),
        get[ParsedValue[String]](map, description_token)
      )
    })

  def propertyDefinitionsEntry(indentLevel: Int) = (keyValue ~ (keyComplexSeparatorPattern ~> propertyDefinition(indentLevel))) ^^ entryParseResultHandler

  def attributeDefinitionEntry(indentLevel: Int) =
    textEntry(indentLevel)(type_token) |
      textEntry(indentLevel)(default_token) |
      textEntry(indentLevel)(description_token)

  def attributeDefinition(indentLevel: Int): Parser[AttributeDefinition] = positioned(
    map(attributeDefinitionEntry)(indentLevel) ^^ {
      case map => AttributeDefinition(
        get[ParsedValue[String]](map, type_token).getOrElse(ParsedValue(Field.STRING)),
        get[ParsedValue[String]](map, description_token),
        get[ParsedValue[String]](map, default_token)
      )
    })

  def attributeDefinitionsEntry(indentLevel: Int) = (keyValue ~ (keyComplexSeparatorPattern ~> attributeDefinition(indentLevel))) ^^ entryParseResultHandler

  def requirementDefinitionEntry(indentLevel: Int) =
    textEntry(indentLevel)(type_token) |
      textEntry(indentLevel)(relationship_type_token) |
      intEntry(indentLevel)(lower_bound_token) |
      intEntry(indentLevel)(upper_bound_token)

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
    textEntry(indentLevel)(type_token) |
      intEntry(indentLevel)(upper_bound_token) |
      mapEntry(indentLevel)(properties_token)(propertyDefinitionsEntry)

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
    (keyValue ~ ((keyComplexSeparatorPattern ~> capabilityDefinition(indentLevel)) | (keyValueSeparatorPattern ~> simpleCapabilityDefinition(indentLevel) <~ newLinePattern))) ^^ entryParseResultHandler

  // From here
  def function(indentLevel: Int) = positioned(("""\{ *""".r ~> (wrapParserWithPosition("get_property" | "get_attribute") <~ keyValueSeparatorPattern <~ """\[ *""".r) ~ (wrapParserWithPosition("SELF" | "HOST" | "SOURCE" | "TARGET") <~ """ *, *""".r) ~ (keyValue <~ """ *\] *\}""".r)) ^^ {
    case (function ~ entity ~ path) => Function(function, entity, path)
  })

  def operationInputEntry(indentLevel: Int) = ((keyValue <~ keyValueSeparatorPattern) ~ (function(indentLevel) | textValue) <~ newLinePattern) ^^ entryParseResultHandler

  def operationEntry(indentLevel: Int) =
    textEntry(indentLevel)(description_token) |
      mapEntry(indentLevel)(inputs_token)(operationInputEntry) |
      textEntry(indentLevel)(implementation_token)

  def operation(indentLevel: Int) = positioned(
    map(operationEntry)(indentLevel) ^^ {
      case map => Operation(
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], Any]](map, inputs_token),
        get[ParsedValue[String]](map, implementation_token))
    })

  def simpleOperation(indentLevel: Int) = positioned(textValue ^^ {
    case implementation => Operation(None, None, if (implementation.value == null || implementation.value.isEmpty) None else Some(implementation))
  })

  def operationsEntry(indentLevel: Int) = (keyValue ~ ((keyComplexSeparatorPattern ~> operation(indentLevel)) | (keyValueSeparatorPattern ~> simpleOperation(indentLevel) <~ newLinePattern))) ^^ entryParseResultHandler

  def interfaceDefinitionEntry(indentLevel: Int) =
    textEntry(indentLevel)(description_token) |
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
    booleanEntry(indentLevel)(abstract_token) |
      textEntry(indentLevel)(derived_from_token) |
      textEntry(indentLevel)(description_token) |
      mapEntry(indentLevel)(tags_token)(genericTextEntry) |
      mapEntry(indentLevel)(properties_token)(propertyDefinitionsEntry) |
      mapEntry(indentLevel)(attributes_token)(attributeDefinitionsEntry) |
      mapEntry(indentLevel)(requirements_token)(requirementDefinitionsEntry) |
      mapEntry(indentLevel)(capabilities_token)(capabilityDefinitionsEntry) |
      listEntry(indentLevel)(artifacts_token)(genericTextEntry) |
      mapEntry(indentLevel)(interfaces_token)(interfaceDefinitionsEntry)

  def nodeType(nodeTypeName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(nodeTypeEntry)(indentLevel) ^^ {
      case map => NodeType(
        nodeTypeName,
        get[ParsedValue[Boolean]](map, abstract_token).getOrElse(ParsedValue(false)),
        get[ParsedValue[String]](map, derived_from_token),
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], ParsedValue[String]]](map, tags_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, properties_token),
        get[Map[ParsedValue[String], AttributeDefinition]](map, attributes_token),
        get[Map[ParsedValue[String], RequirementDefinition]](map, requirements_token),
        get[Map[ParsedValue[String], CapabilityDefinition]](map, capabilities_token),
        get[Map[ParsedValue[String], ParsedValue[String]]](map, artifacts_token),
        get[Map[ParsedValue[String], Interface]](map, interfaces_token))
    })

  def nodeTypesEntry(indentLevel: Int) = (keyValue into (nodeTypeName => keyComplexSeparatorPattern ~> nodeType(nodeTypeName)(indentLevel + 1))) ^^ {
    case nodeType => (nodeType.name, nodeType)
  }

  def functionGetInput(indentLevel: Int) = positioned(("""\{ *""".r ~> "get_input" ~> keyValueSeparatorPattern ~> keyValue <~ """ *\}""".r) ^^ {
    case inputName => Input(inputName)
  })

  def propertyInputEntry(indentLevel: Int) = ((keyValue <~ keyValueSeparatorPattern) ~ (functionGetInput(indentLevel) | textValue) <~ newLinePattern) ^^ entryParseResultHandler

  def properties(indentLevel: Int) = map(propertyInputEntry)(indentLevel)

  def requirementEntry(indentLevel: Int) =
    textEntry(indentLevel)(node_token) |
      textEntry(indentLevel)(capability_token)

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
    keyValue into (requirementName => (keyComplexSeparatorPattern ~> requirement(requirementName)(indentLevel + 1)) | (keyValueSeparatorPattern ~> simpleRequirement(requirementName)(indentLevel) <~ newLinePattern))

  def requirements(indentLevel: Int) = list(requirementsEntry)(indentLevel)

  def nodeTemplateEntry(indentLevel: Int) =
    textEntry(indentLevel)(type_token) |
      complexEntry(indentLevel)(properties_token)(properties) |
      complexEntry(indentLevel)(requirements_token)(requirements)

  def nodeTemplate(nodeTemplateName: ParsedValue[String])(indentLevel: Int) =
    positioned(map(nodeTemplateEntry)(indentLevel) ^^ {
      case map => NodeTemplate(
        nodeTemplateName,
        get[ParsedValue[String]](map, type_token),
        get[Map[ParsedValue[String], Any]](map, properties_token),
        get[List[Requirement]](map, requirements_token))
    })

  def nodeTemplatesEntry(indentLevel: Int) = (keyValue into (nodeTemplateName => keyComplexSeparatorPattern ~> nodeTemplate(nodeTemplateName)(indentLevel + 1))) ^^ {
    case nodeTemplate => (nodeTemplate.name, nodeTemplate)
  }

  def topologyTemplateEntry(indentLevel: Int) =
    textEntry(indentLevel)(description_token) |
      mapEntry(indentLevel)(inputs_token)(propertyDefinitionsEntry) |
      mapEntry(indentLevel)(node_templates_token)(nodeTemplatesEntry)

  def topologyTemplate(indentLevel: Int) =
    positioned(map(topologyTemplateEntry)(indentLevel) ^^ {
      case map => TopologyTemplate(
        get[ParsedValue[String]](map, description_token),
        get[Map[ParsedValue[String], PropertyDefinition]](map, inputs_token),
        get[Map[ParsedValue[String], NodeTemplate]](map, node_templates_token))
    })

  def definitionEntry(indentLevel: Int) =
    textEntry(indentLevel)(definitions_version_token) |
      textEntry(indentLevel)(template_name_token) |
      textEntry(indentLevel)(template_version_token) |
      textEntry(indentLevel)(template_author_token) |
      textEntry(indentLevel)(description_token) |
      mapEntry(indentLevel)(node_types_token)(nodeTypesEntry) |
      mapEntry(indentLevel)(capability_types_token)(capabilityTypesEntry) |
      mapEntry(indentLevel)(relationship_types_token)(relationshipTypesEntry) |
      mapEntry(indentLevel)(artifact_types_token)(artifactTypesEntry) |
      complexEntry(indentLevel)(topology_template_token)(topologyTemplate)

  def definition =
    positioned(phrase(opt(newLinePattern) ~> map(definitionEntry)(0) <~ opt(newLinePattern)) ^^ {
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
