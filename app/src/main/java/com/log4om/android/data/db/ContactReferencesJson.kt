package com.log4om.android.data.db

import com.log4om.android.data.model.Qso

/**
 * Log4OM2 stores SOTA/POTA/IOTA/WWFF/COTA in the `contactreferences` JSON column,
 * not in dedicated ADIF-style columns. Empty means NULL — never required.
 */
object ContactReferencesJson {

    data class Refs(
        val sota: String = "",
        val iota: String = "",
        val pota: String = "",
        val wwff: String = "",
        val cota: String = "",
        val extras: List<Pair<String, String>> = emptyList()
    ) {
        fun isEmpty(): Boolean =
            sota.isBlank() && iota.isBlank() && pota.isBlank() &&
                wwff.isBlank() && cota.isBlank() && extras.isEmpty()
    }

    private val MANAGED = setOf("SOTA", "IOTA", "POTA", "WWFF", "COTA")
    private val OBJECT = Regex("""\{[^{}]*\}""")
    private val KV = Regex(""""(\w+)"\s*:\s*"((?:\\.|[^"\\])*)"""")

    fun fromQso(q: Qso) = Refs(
        sota = q.sotaRef,
        iota = q.iota,
        pota = q.potaRef,
        wwff = q.wwffRef,
        cota = q.cotaRef
    )

    fun encode(refs: Refs, existingJson: String? = null): String? {
        val merged = merge(parse(existingJson), refs)
        if (merged.isEmpty()) return null
        val items = buildList {
            merged.extras.forEach { (award, ref) ->
                if (award.isNotBlank() && ref.isNotBlank()) add(award to ref)
            }
            addManaged("SOTA", merged.sota)
            addManaged("IOTA", merged.iota)
            addManaged("POTA", merged.pota)
            addManaged("WWFF", merged.wwff)
            addManaged("COTA", merged.cota)
        }
        if (items.isEmpty()) return null
        return items.joinToString(",", "[", "]") { (award, ref) ->
            """{"Award":"${escape(award)}","Reference":"${escape(ref)}"}"""
        }
    }

    fun parse(json: String?): Refs {
        if (json.isNullOrBlank() || json.trim() == "[]" || json.trim() == "null") return Refs()
        var sota = ""
        var iota = ""
        var pota = ""
        var wwff = ""
        var cota = ""
        val extras = mutableListOf<Pair<String, String>>()
        OBJECT.findAll(json).forEach { match ->
            val map = linkedMapOf<String, String>()
            KV.findAll(match.value).forEach { kv ->
                map[kv.groupValues[1].lowercase()] = unescape(kv.groupValues[2])
            }
            val award = (map["award"] ?: map["id"] ?: map["a"] ?: map["code"] ?: "").trim()
            val ref = (map["reference"] ?: map["r"] ?: map["ref"] ?: map["value"] ?: "").trim()
            if (award.isBlank() || ref.isBlank()) return@forEach
            when (award.uppercase()) {
                "SOTA" -> sota = ref
                "IOTA" -> iota = ref
                "POTA" -> pota = ref
                "WWFF" -> wwff = ref
                "COTA" -> cota = ref
                else -> extras += award to ref
            }
        }
        return Refs(sota, iota, pota, wwff, cota, extras)
    }

    private fun MutableList<Pair<String, String>>.addManaged(award: String, ref: String) {
        val v = ref.trim()
        if (v.isNotEmpty()) add(award to v)
    }

    private fun merge(existing: Refs, incoming: Refs): Refs {
        val extras = existing.extras.filter { it.first.uppercase() !in MANAGED } +
            incoming.extras.filter { it.first.uppercase() !in MANAGED }
        return Refs(
            sota = incoming.sota.trim(),
            iota = incoming.iota.trim(),
            pota = incoming.pota.trim(),
            wwff = incoming.wwff.trim(),
            cota = incoming.cota.trim(),
            extras = extras
        )
    }

    private fun escape(s: String): String = buildString(s.length) {
        for (c in s) {
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
    }

    private fun unescape(s: String): String = buildString(s.length) {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(s[i + 1])
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
