package com.log4om.api.qso

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Component

@Component
class QsoDao {
    private val jdbcFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun queryFiltered(conn: Connection, filter: LogFilterDto, limit: Int, offset: Int): List<QsoDto> {
        val built = LogFilterSql.build(filter)
        val sql = "SELECT * FROM log ${built.whereSql} ORDER BY qsodate DESC LIMIT ? OFFSET ?"
        return conn.prepareStatement(sql).use { stmt ->
            val next = built.bind(stmt, 1)
            stmt.setInt(next, limit)
            stmt.setInt(next + 1, offset)
            stmt.executeQuery().use { rs -> rs.toList() }
        }
    }

    fun countFiltered(conn: Connection, filter: LogFilterDto): Int {
        val built = LogFilterSql.build(filter)
        val sql = "SELECT COUNT(*) FROM log ${built.whereSql}"
        return conn.prepareStatement(sql).use { stmt ->
            built.bind(stmt, 1)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun getFilteredIds(conn: Connection, filter: LogFilterDto): List<Long> {
        val built = LogFilterSql.build(filter)
        val sql = "SELECT qsoid FROM log ${built.whereSql} ORDER BY qsodate DESC"
        return conn.prepareStatement(sql).use { stmt ->
            built.bind(stmt, 1)
            stmt.executeQuery().use { rs ->
                buildList { while (rs.next()) add(rs.getLong(1)) }
            }
        }
    }

    fun getByIds(conn: Connection, ids: List<Long>): List<QsoDto> {
        if (ids.isEmpty()) return emptyList()
        val ordered = ArrayList<QsoDto>(ids.size)
        ids.chunked(400).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            conn.prepareStatement("SELECT * FROM log WHERE qsoid IN ($placeholders)").use { stmt ->
                chunk.forEachIndexed { i, id -> stmt.setLong(i + 1, id) }
                val byId = stmt.executeQuery().use { rs -> rs.toList().associateBy { it.qsoid!! } }
                chunk.forEach { id -> byId[id]?.let { ordered += it } }
            }
        }
        return ordered
    }

    fun getByCallsign(conn: Connection, callsign: String, limit: Int): List<QsoDto> {
        return conn.prepareStatement(
            "SELECT * FROM log WHERE callsign LIKE ? ORDER BY qsodate DESC LIMIT ?"
        ).use { stmt ->
            stmt.setString(1, callsign.uppercase() + "%")
            stmt.setInt(2, limit)
            stmt.executeQuery().use { it.toList() }
        }
    }

    fun getWorkedDxccIds(conn: Connection): Set<Int> =
        conn.createStatement().use { st ->
            st.executeQuery("SELECT DISTINCT dxcc FROM log WHERE dxcc > 0").use { rs ->
                buildSet { while (rs.next()) add(rs.getInt(1)) }
            }
        }

    fun getWorkedDxccBands(conn: Connection): Set<Pair<Int, String>> =
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT DISTINCT dxcc, band FROM log WHERE dxcc > 0 AND band IS NOT NULL AND band <> ''"
            ).use { rs ->
                buildSet { while (rs.next()) add(rs.getInt(1) to rs.getString(2)) }
            }
        }

    fun insert(conn: Connection, q: QsoDto): Boolean {
        return conn.prepareStatement(INSERT_SQL).use { stmt ->
            stmt.bindQso(q, null)
            stmt.executeUpdate() > 0
        }
    }

    fun update(conn: Connection, q: QsoDto): Boolean {
        val existing = conn.prepareStatement("SELECT contactreferences FROM log WHERE qsoid=?").use { stmt ->
            stmt.setLong(1, q.qsoid ?: return false)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }
        return conn.prepareStatement(UPDATE_SQL).use { stmt ->
            stmt.bindQsoUpdate(q, existing)
            stmt.executeUpdate() > 0
        }
    }

    fun delete(conn: Connection, qsoid: Long): Boolean =
        conn.prepareStatement("DELETE FROM log WHERE qsoid=?").use { stmt ->
            stmt.setLong(1, qsoid)
            stmt.executeUpdate() > 0
        }

    fun bulkInsert(conn: Connection, qsos: List<QsoDto>, chunkSize: Int = 500): BulkInsertResultDto {
        val prev = conn.autoCommit
        conn.autoCommit = false
        var inserted = 0
        var skipped = 0
        try {
            conn.prepareStatement(INSERT_IGNORE_SQL).use { stmt ->
                qsos.forEachIndexed { idx, q ->
                    stmt.bindQso(q, null)
                    stmt.addBatch()
                    if ((idx + 1) % chunkSize == 0) {
                        stmt.executeBatch().forEach { if (it > 0) inserted++ else skipped++ }
                        conn.commit()
                    }
                }
                stmt.executeBatch().forEach { if (it > 0) inserted++ else skipped++ }
                conn.commit()
            }
        } catch (e: Exception) {
            conn.rollback()
            throw e
        } finally {
            conn.autoCommit = prev
        }
        return BulkInsertResultDto(inserted, skipped)
    }

    private val INSERT_SQL = """
        INSERT INTO log (
            qsoid, callsign, band, mode, qsodate,
            freq, freqrx, rstsent, rstrcvd,
            name, qth, country, dxcc, cqzone, ituzone,
            gridsquare, cont, comment, notes,
            stationcallsign, mygridsquare, myname, myrig, operator,
            txpwr, propmode, contestid,
            address, email, pfx, state, cnty,
            qslvia, qslmsg, eqcall,
            qsocomplete, qsorandom, swl, satelliteqso,
            satmode, satname, srxstring, stxstring,
            contactedop, programid, programversion,
            `class`, antpath, antenna, bandrx, callsignurl,
            contactassociations, precedence, ownercallsign,
            sig, siginfo, mysig, mysiginfo,
            mycity, mycnty, mycountry, mypostalcode, mystate, mystreet,
            mydxcc, mylat, mylon,
            forceinit, mufday, reliability, signaltonoiseratio, sunspots,
            lat, lon, distance,
            contactreferences
        ) VALUES (
            ?,?,?,?,?, ?,?,?,?, ?,?,?,?,?,?, ?,?,?,?,
            ?,?,?,?,?, ?,?,?, ?,?,?,?,?, ?,?,?, ?,?,?,?,
            ?,?,?,?, ?,?,?, ?,?,?,?,?, ?,?,?, ?,?,?,?,
            ?,?,?,?,?,?, ?,?,?, ?,?,?,?,?, ?,?,?, ?,?,?
        )
    """.trimIndent()

    private val INSERT_IGNORE_SQL =
        INSERT_SQL.replaceFirst("INSERT INTO log", "INSERT IGNORE INTO log")

    private val UPDATE_SQL = """
        UPDATE log SET
            callsign=?, band=?, bandrx=?, mode=?, qsodate=?,
            freq=?, freqrx=?, rstsent=?, rstrcvd=?,
            name=?, qth=?, country=?, dxcc=?, cqzone=?, ituzone=?,
            gridsquare=?, cont=?, comment=?, notes=?,
            stationcallsign=?, mygridsquare=?, myname=?, myrig=?,
            mycountry=?, mydxcc=?, mylat=?, mylon=?,
            txpwr=?, propmode=?, contestid=?,
            address=?, email=?, pfx=?, state=?, cnty=?,
            qslvia=?, qslmsg=?, eqcall=?,
            contactedop=?, srxstring=?, stxstring=?,
            satmode=?, satname=?, satelliteqso=?,
            operator=?, sig=?, siginfo=?,
            lat=?, lon=?, distance=?,
            contactreferences=?
        WHERE qsoid=?
    """.trimIndent()

    private fun PreparedStatement.bindQso(q: QsoDto, existingRefsJson: String?) {
        var i = 1
        setLong(i++, q.qsoid ?: System.currentTimeMillis())
        setString(i++, q.callsign.uppercase())
        setString(i++, q.band)
        setString(i++, q.mode)
        setTimestamp(i++, Timestamp.valueOf(q.qsodate.format(jdbcFmt)))
        setDouble(i++, q.freq)
        setDouble(i++, q.freqrx)
        setString(i++, q.rstsent)
        setString(i++, q.rstrcvd)
        setString(i++, q.name)
        setString(i++, q.qth)
        setString(i++, q.country)
        setInt(i++, q.dxcc)
        if (q.cqzone != null) setInt(i++, q.cqzone) else setNull(i++, Types.INTEGER)
        if (q.ituzone != null) setInt(i++, q.ituzone) else setNull(i++, Types.INTEGER)
        setString(i++, q.gridsquare)
        setString(i++, q.cont)
        setString(i++, q.comment)
        setString(i++, q.notes)
        setString(i++, q.stationcallsign)
        setString(i++, q.mygridsquare)
        setString(i++, q.myname)
        setString(i++, q.myrig)
        setString(i++, q.operator)
        if (q.txpwr != null) setDouble(i++, q.txpwr) else setNull(i++, Types.DECIMAL)
        setString(i++, q.propmode)
        setString(i++, q.contestid)
        setString(i++, q.address)
        setString(i++, "") // email
        setString(i++, "") // pfx
        setString(i++, "") // state
        setString(i++, "") // cnty
        setString(i++, "") // qslvia
        setString(i++, "") // qslmsg
        setNull(i++, Types.VARCHAR) // eqcall
        setString(i++, "") // qsocomplete
        setInt(i++, 1) // qsorandom
        setInt(i++, 0) // swl
        setInt(i++, q.satelliteqso)
        setString(i++, q.satmode)
        setString(i++, q.satname)
        setString(i++, "") // srxstring
        setString(i++, "") // stxstring
        setString(i++, "") // contactedop
        setString(i++, q.programid)
        setString(i++, q.programversion)
        setString(i++, "") // class
        setString(i++, "") // antpath
        setString(i++, "") // antenna
        setString(i++, q.bandrx.ifBlank { q.band })
        setString(i++, "") // callsignurl
        setString(i++, "") // contactassociations
        setString(i++, "") // precedence
        setString(i++, "") // ownercallsign
        setString(i++, "") // sig
        setString(i++, "") // siginfo
        setString(i++, "") // mysig
        setString(i++, "") // mysiginfo
        setString(i++, "") // mycity
        setString(i++, "") // mycnty
        setString(i++, q.mycountry)
        setString(i++, "") // mypostalcode
        setString(i++, "") // mystate
        setString(i++, "") // mystreet
        if (q.mydxcc != null) setInt(i++, q.mydxcc) else setNull(i++, Types.INTEGER)
        if (q.mylat != null) setDouble(i++, q.mylat) else setNull(i++, Types.DECIMAL)
        if (q.mylon != null) setDouble(i++, q.mylon) else setNull(i++, Types.DECIMAL)
        setInt(i++, 0) // forceinit
        setDouble(i++, 0.0) // mufday
        setDouble(i++, 0.0) // reliability
        setDouble(i++, 0.0) // snr
        setInt(i++, 0) // sunspots
        if (q.lat != null) setDouble(i++, q.lat) else setNull(i++, Types.DECIMAL)
        if (q.lon != null) setDouble(i++, q.lon) else setNull(i++, Types.DECIMAL)
        if (q.distance != null) setDouble(i++, q.distance) else setNull(i++, Types.DECIMAL)
        bindContactReferences(i, q, existingRefsJson)
    }

    private fun PreparedStatement.bindQsoUpdate(q: QsoDto, existingRefsJson: String?) {
        var i = 1
        setString(i++, q.callsign.uppercase())
        setString(i++, q.band)
        setString(i++, q.bandrx.ifBlank { q.band })
        setString(i++, q.mode)
        setTimestamp(i++, Timestamp.valueOf(q.qsodate.format(jdbcFmt)))
        setDouble(i++, q.freq)
        setDouble(i++, q.freqrx)
        setString(i++, q.rstsent)
        setString(i++, q.rstrcvd)
        setString(i++, q.name)
        setString(i++, q.qth)
        setString(i++, q.country)
        setInt(i++, q.dxcc)
        if (q.cqzone != null) setInt(i++, q.cqzone) else setNull(i++, Types.INTEGER)
        if (q.ituzone != null) setInt(i++, q.ituzone) else setNull(i++, Types.INTEGER)
        setString(i++, q.gridsquare)
        setString(i++, q.cont)
        setString(i++, q.comment)
        setString(i++, q.notes)
        setString(i++, q.stationcallsign)
        setString(i++, q.mygridsquare)
        setString(i++, q.myname)
        setString(i++, q.myrig)
        setString(i++, q.mycountry)
        if (q.mydxcc != null) setInt(i++, q.mydxcc) else setNull(i++, Types.INTEGER)
        if (q.mylat != null) setDouble(i++, q.mylat) else setNull(i++, Types.DECIMAL)
        if (q.mylon != null) setDouble(i++, q.mylon) else setNull(i++, Types.DECIMAL)
        if (q.txpwr != null) setDouble(i++, q.txpwr) else setNull(i++, Types.DECIMAL)
        setString(i++, q.propmode)
        setString(i++, q.contestid)
        setString(i++, q.address)
        setString(i++, "")
        setString(i++, "")
        setString(i++, "")
        setString(i++, "")
        setString(i++, "")
        setString(i++, "")
        setNull(i++, Types.VARCHAR)
        setString(i++, "")
        setString(i++, "")
        setString(i++, "")
        setString(i++, q.satmode)
        setString(i++, q.satname)
        setInt(i++, q.satelliteqso)
        setString(i++, q.operator)
        setString(i++, "")
        setString(i++, "")
        if (q.lat != null) setDouble(i++, q.lat) else setNull(i++, Types.DECIMAL)
        if (q.lon != null) setDouble(i++, q.lon) else setNull(i++, Types.DECIMAL)
        if (q.distance != null) setDouble(i++, q.distance) else setNull(i++, Types.DECIMAL)
        i = bindContactReferences(i, q, existingRefsJson)
        setLong(i, q.qsoid!!)
    }

    private fun PreparedStatement.bindContactReferences(
        index: Int,
        q: QsoDto,
        existingJson: String?
    ): Int {
        val json = ContactReferencesJson.encode(ContactReferencesJson.fromQso(q), existingJson)
        if (json == null) setNull(index, Types.NULL) else setString(index, json)
        return index + 1
    }

    private fun ResultSet.toList(): List<QsoDto> {
        val result = mutableListOf<QsoDto>()
        while (next()) result += toQso()
        return result
    }

    private fun ResultSet.str(col: String): String = getString(col) ?: ""
    private fun ResultSet.softStr(col: String): String =
        try { getString(col) ?: "" } catch (_: Exception) { "" }
    private fun ResultSet.optInt(col: String): Int? = getObject(col)?.let { (it as Number).toInt() }
    private fun ResultSet.optDouble(col: String): Double? = getObject(col)?.let { (it as Number).toDouble() }

    private fun ResultSet.toQso(): QsoDto {
        val awards = ContactReferencesJson.parse(softStr("contactreferences"))
        val ts = getTimestamp("qsodate")
        return QsoDto(
            qsoid = getLong("qsoid"),
            callsign = str("callsign"),
            band = str("band"),
            mode = str("mode"),
            qsodate = ts?.toLocalDateTime() ?: LocalDateTime.now(),
            freq = getDouble("freq"),
            freqrx = softDouble("freqrx"),
            rstsent = str("rstsent"),
            rstrcvd = str("rstrcvd"),
            name = str("name"),
            address = softStr("address"),
            qth = softStr("qth"),
            country = softStr("country"),
            dxcc = try { getInt("dxcc") } catch (_: Exception) { 0 },
            cqzone = optInt("cqzone"),
            ituzone = optInt("ituzone"),
            gridsquare = softStr("gridsquare"),
            cont = softStr("cont"),
            comment = softStr("comment"),
            notes = softStr("notes"),
            txpwr = optDouble("txpwr"),
            propmode = softStr("propmode"),
            contestid = softStr("contestid"),
            satmode = softStr("satmode"),
            satname = softStr("satname"),
            satelliteqso = try { getInt("satelliteqso") } catch (_: Exception) { 0 },
            stationcallsign = softStr("stationcallsign"),
            mygridsquare = softStr("mygridsquare"),
            myname = softStr("myname"),
            myrig = softStr("myrig"),
            mycountry = softStr("mycountry"),
            mydxcc = optInt("mydxcc"),
            mylat = optDouble("mylat"),
            mylon = optDouble("mylon"),
            operator = softStr("operator"),
            bandrx = softStr("bandrx"),
            lat = optDouble("lat"),
            lon = optDouble("lon"),
            distance = optDouble("distance"),
            sotaRef = awards.sota.ifBlank { softStr("sota_ref") },
            iota = awards.iota.ifBlank { softStr("iota") },
            potaRef = awards.pota.ifBlank { softStr("pota_ref") },
            wwffRef = awards.wwff.ifBlank { softStr("wwff_ref") },
            cotaRef = awards.cota.ifBlank { softStr("cota_ref") },
            programid = softStr("programid").ifBlank { "Log4OM API" },
            programversion = softStr("programversion").ifBlank { "0.1.0" }
        )
    }

    private fun ResultSet.softDouble(col: String): Double =
        try { getDouble(col) } catch (_: Exception) { 0.0 }
}
