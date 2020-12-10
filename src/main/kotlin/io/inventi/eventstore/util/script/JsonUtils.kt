package io.inventi.eventstore.util.script

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.DecimalNode
import com.fasterxml.jackson.databind.node.NullNode
import com.fasterxml.jackson.databind.node.NumericNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.databind.node.TextNode
import java.math.BigDecimal
import java.time.LocalDate

fun ObjectNode.stringValue(fieldName: String): String = stringValueOrNull(fieldName)!!
fun ObjectNode.stringValueOrNull(fieldName: String): String? = getOrNull<TextNode>(fieldName)?.textValue()

fun ObjectNode.decimalValue(fieldName: String): BigDecimal = decimalValueOrNull(fieldName)!!
fun ObjectNode.decimalValueOrNull(fieldName: String): BigDecimal? = getOrNull<NumericNode>(fieldName)?.decimalValue()

fun ObjectNode.localDateValue(fieldName: String): LocalDate = localDateValueOrNull(fieldName)!!
fun ObjectNode.localDateValueOrNull(fieldName: String): LocalDate? = getOrNull<TextNode>(fieldName)?.let { LocalDate.parse(it.textValue()) }

private inline fun <reified T : JsonNode> ObjectNode.getOrNull(fieldName: String): T? = get(fieldName)
        ?.takeIf { it !is NullNode }
        ?.let { it as T }

fun BigDecimal.toDecimalNode() = DecimalNode(this)