package com.log4om.android.data.refs

import org.json.JSONObject
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * File-backed Europe activity catalogue under [root] (typically `cacheDir/refs/`).
 * One JSONL file per program: compact keys for size.
 */
class ReferenceCatalog(private val root: File) {

    init {
        root.mkdirs()
    }

    fun fileFor(program: ActivityProgram): File = File(root, "${program.name.lowercase()}.jsonl")

    fun count(program: ActivityProgram): Int {
        val f = fileFor(program)
        if (!f.exists()) return 0
        return f.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            var n = 0
            while (reader.readLine() != null) n++
            n
        }
    }

    fun totalCount(): Int = ActivityProgram.entries.sumOf { count(it) }

    fun isEmpty(): Boolean = totalCount() == 0

    fun load(program: ActivityProgram): List<ActivityRef> {
        val f = fileFor(program)
        if (!f.exists()) return emptyList()
        return f.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line -> parseLine(program, line) }.toList()
        }
    }

    fun loadAll(): List<ActivityRef> =
        ActivityProgram.entries.flatMap { load(it) }

    fun replace(program: ActivityProgram, refs: Sequence<ActivityRef>) {
        root.mkdirs()
        val target = fileFor(program)
        val tmp = File(root, "${program.name.lowercase()}.jsonl.tmp")
        BufferedWriter(
            OutputStreamWriter(FileOutputStream(tmp), StandardCharsets.UTF_8)
        ).use { out ->
            refs.forEach { ref ->
                out.append(encode(ref))
                out.newLine()
            }
        }
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
    }

    fun clear(program: ActivityProgram) {
        fileFor(program).delete()
    }

    private fun encode(ref: ActivityRef): String {
        val o = JSONObject()
        o.put("r", ref.reference)
        o.put("n", ref.name)
        o.put("lat", ref.lat)
        o.put("lon", ref.lon)
        if (ref.country.isNotBlank()) o.put("c", ref.country)
        ref.bbox?.let { b ->
            if (b.size == 4) {
                o.put("s", b[0])
                o.put("w", b[1])
                o.put("n", b[2])
                o.put("e", b[3])
            }
        }
        return o.toString()
    }

    private fun parseLine(program: ActivityProgram, line: String): ActivityRef? {
        if (line.isBlank()) return null
        return runCatching {
            val o = JSONObject(line)
            val lat = o.getDouble("lat")
            val lon = o.getDouble("lon")
            val bbox = if (o.has("s") && o.has("w") && o.has("n") && o.has("e")) {
                doubleArrayOf(o.getDouble("s"), o.getDouble("w"), o.getDouble("n"), o.getDouble("e"))
            } else null
            ActivityRef(
                program = program,
                reference = o.getString("r"),
                name = o.optString("n", ""),
                lat = lat,
                lon = lon,
                country = o.optString("c", ""),
                bbox = bbox
            )
        }.getOrNull()
    }
}
