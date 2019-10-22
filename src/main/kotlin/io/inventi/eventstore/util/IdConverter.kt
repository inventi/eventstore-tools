package io.inventi.eventstore.util


object IdConverter {
    private val DELIMETER = "-"

    fun guidToUuid(guuid: String): String {
        val parts = guuid.split(DELIMETER)
        val first3 = parts.take(3)
        val last3 = parts.drop(3)

        val swapped = first3.map(::swapBytes).joinToString(DELIMETER)
        val joined = last3.joinToString(DELIMETER)

        return "${swapped}-$joined"
    }

    private fun swapBytes(part: String): String {
        return part.chunked(2).reversed().joinToString("")
    }
}