package com.log4om.android.data.refs

/** Minimal RFC4180-ish CSV splitter (quoted fields with commas). */
object CsvLineParser {
    fun split(line: String): List<String> {
        val out = ArrayList<String>(16)
        val sb = StringBuilder()
        var i = 0
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    out += sb.toString()
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        out += sb.toString()
        return out
    }
}
