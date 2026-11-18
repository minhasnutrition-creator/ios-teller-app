package java.util.regex

class Pattern private constructor(val regexStr: String, val flags: Int) {
    private val regex: Regex = if (flags and CASE_INSENSITIVE != 0) {
        Regex(regexStr, RegexOption.IGNORE_CASE)
    } else {
        Regex(regexStr)
    }

    fun matcher(input: CharSequence): Matcher = Matcher(regex, input.toString())
    fun split(input: CharSequence): Array<String> = regex.split(input).toTypedArray()
    fun pattern(): String = regexStr

    companion object {
        const val CASE_INSENSITIVE = 2
        const val MULTILINE = 8
        const val DOTALL = 32

        fun compile(regex: String): Pattern = Pattern(regex, 0)
        fun compile(regex: String, flags: Int): Pattern = Pattern(regex, flags)
        fun quote(s: String): String = Regex.escape(s)
    }
}

class Matcher(private val regex: Regex, private val input: String) {
    private var matchResult: MatchResult? = null
    private var matchResults: List<MatchResult>? = null
    private var currentIndex = 0

    fun find(): Boolean {
        if (matchResults == null) {
            matchResults = regex.findAll(input).toList()
        }
        val list = matchResults ?: emptyList()
        if (currentIndex < list.size) {
            matchResult = list[currentIndex++]
            return true
        }
        return false
    }

    fun matches(): Boolean {
        matchResult = regex.matchEntire(input)
        return matchResult != null
    }

    fun groupCount(): Int = (matchResult?.groups?.size ?: 1) - 1

    fun group(): String? = matchResult?.value

    fun group(groupIndex: Int): String? {
        val mr = matchResult ?: return null
        return if (groupIndex < mr.groups.size) mr.groups[groupIndex]?.value else null
    }

    fun start(): Int = matchResult?.range?.first ?: -1
    fun start(groupIndex: Int): Int {
        val mr = matchResult ?: return -1
        return if (groupIndex < mr.groups.size) mr.groups[groupIndex]?.range?.first ?: -1 else -1
    }

    fun end(): Int = (matchResult?.range?.last ?: -2) + 1
    fun end(groupIndex: Int): Int {
        val mr = matchResult ?: return -1
        return if (groupIndex < mr.groups.size) (mr.groups[groupIndex]?.range?.last ?: -2) + 1 else -1
    }

    fun replaceAll(replacement: String): String = regex.replace(input, replacement)
    fun replaceFirst(replacement: String): String = regex.replaceFirst(input, replacement)
}
