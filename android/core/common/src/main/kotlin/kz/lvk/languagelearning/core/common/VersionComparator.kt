package kz.lvk.languagelearning.core.common

object VersionComparator {
    fun isNewer(candidateVersion: String, currentVersion: String): Boolean {
        val candidate = candidateVersion.toNumericParts()
        val current = currentVersion.toNumericParts()
        val maxSize = maxOf(candidate.size, current.size)

        repeat(maxSize) { index ->
            val left = candidate.getOrElse(index) { 0 }
            val right = current.getOrElse(index) { 0 }
            if (left != right) return left > right
        }
        return false
    }

    private fun String.toNumericParts(): List<Int> =
        substringBefore('-')
            .split('.')
            .map { part -> part.filter(Char::isDigit).toIntOrNull() ?: 0 }
}
