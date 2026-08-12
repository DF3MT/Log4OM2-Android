package com.log4om.android.data.db

import com.log4om.android.data.model.LogFilter
import com.log4om.android.data.model.Qso
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val JDBC_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

data class BulkInsertResult(val inserted: Int, val skipped: Int)

class QsoDao(private val db: DatabaseHelper) {

    suspend fun getRecentQsos(limit: Int = 100, offset: Int = 0): Result<List<Qso>> =
        queryFiltered(LogFilter(), limit, offset)

    suspend fun searchQsos(query: String, limit: Int = 100, offset: Int = 0): Result<List<Qso>> =
        queryFiltered(LogFilter(callsign = query), limit, offset)

    suspend fun queryFiltered(filter: LogFilter, limit: Int = 100, offset: Int = 0): Result<List<Qso>> =
        db.withConnection { conn ->
            val built = LogFilterSql.build(filter)
            val sql = "SELECT * FROM log ${built.whereSql} ORDER BY qsodate DESC LIMIT ? OFFSET ?"
            conn.prepareStatement(sql).use { stmt ->
                val next = built.bind(stmt, 1)
                stmt.setInt(next, limit)
                stmt.setInt(next + 1, offset)
                stmt.executeQuery().use { it.toList() }
            }
        }

    suspend fun countFiltered(filter: LogFilter): Result<Int> =
        db.withConnection { conn ->
            val built = LogFilterSql.build(filter)
            val sql = "SELECT COUNT(*) FROM log ${built.whereSql}"
            conn.prepareStatement(sql).use { stmt ->
                built.bind(stmt, 1)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }

    suspend fun getFilteredIds(filter: LogFilter): Result<List<Long>> =
        db.withConnection { conn ->
            val built = LogFilterSql.build(filter)
            val sql = "SELECT qsoid FROM log ${built.whereSql} ORDER BY qsodate DESC"
            conn.prepareStatement(sql).use { stmt ->
                built.bind(stmt, 1)
                stmt.executeQuery().use { rs ->
                    val ids = mutableListOf<Long>()
                    while (rs.next()) ids += rs.getLong(1)
                    ids
                }
            }
        }

    suspend fun getQsosByIds(ids: List<Long>): Result<List<Qso>> {
        if (ids.isEmpty()) return Result.success(emptyList())
        return db.withConnection { conn ->
            val ordered = ArrayList<Qso>(ids.size)
            ids.chunked(400).forEach { chunk ->
                val placeholders = chunk.joinToString(",") { "?" }
                conn.prepareStatement(
                    "SELECT * FROM log WHERE qsoid IN ($placeholders)"
                ).use { stmt ->
                    chunk.forEachIndexed { i, id -> stmt.setLong(i + 1, id) }
                    val byId = stmt.executeQuery().use { rs ->
                        rs.toList().associateBy { it.qsoid }
                    }
                    chunk.forEach { id -> byId[id]?.let { ordered += it } }
                }
            }
            ordered
        }
    }

    suspend fun getQsoCount(): Result<Int> = countFiltered(LogFilter())

    suspend fun insertQso(qso: Qso): Result<Boolean> =
        db.withConnection { conn ->
            conn.prepareStatement(INSERT_SQL).use { stmt ->
                stmt.bindQso(qso)
                stmt.executeUpdate() > 0
            }
        }

    suspend fun updateQso(qso: Qso): Result<Boolean> =
        db.withConnection { conn ->
            conn.prepareStatement(UPDATE_SQL).use { stmt ->
                stmt.bindQsoUpdate(qso)
                stmt.executeUpdate() > 0
            }
        }

    suspend fun deleteQso(qsoid: Long): Result<Boolean> =
        db.withConnection { conn ->
            conn.prepareStatement("DELETE FROM log WHERE qsoid=?").use { stmt ->
                stmt.setLong(1, qsoid)
                stmt.executeUpdate() > 0
            }
        }

    suspend fun getQsosByCallsign(callsign: String, limit: Int = 15): Result<List<Qso>> =
        db.withConnection { conn ->
            conn.prepareStatement(
                "SELECT * FROM log WHERE callsign LIKE ? ORDER BY qsodate DESC LIMIT ?"
            ).use { stmt ->
                stmt.setString(1, callsign.uppercase() + "%")
                stmt.setInt(2, limit)
                stmt.executeQuery().use { it.toList() }
            }
        }

    /** Batches inserts with INSERT IGNORE so duplicates (composite PK) are silently skipped. */
    suspend fun bulkInsert(
        qsos: List<Qso>,
        chunkSize: Int = 500
    ): Result<BulkInsertResult> =
        db.withConnection { conn ->
            val prevAutoCommit = conn.autoCommit
            conn.autoCommit = false
            var inserted = 0
            var skipped = 0
            try {
                conn.prepareStatement(INSERT_IGNORE_SQL).use { stmt ->
                    qsos.forEachIndexed { idx, q ->
                        stmt.bindQso(q)
                        stmt.addBatch()
                        if ((idx + 1) % chunkSize == 0) {
                            val results = stmt.executeBatch()
                            results.forEach { if (it > 0) inserted++ else skipped++ }
                            conn.commit()
                        }
                    }
                    val results = stmt.executeBatch()
                    results.forEach { if (it > 0) inserted++ else skipped++ }
                    conn.commit()
                }
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            } finally {
                conn.autoCommit = prevAutoCommit
            }
            BulkInsertResult(inserted, skipped)
        }

    // --- SQL ---

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
            forceinit, mufday, reliability, signaltonoiseratio, sunspots
        ) VALUES (
            ?,?,?,?,?,
            ?,?,?,?,
            ?,?,?,?,?,?,
            ?,?,?,?,
            ?,?,?,?,?,
            ?,?,?,
            ?,?,?,?,?,
            ?,?,?,
            ?,?,?,?,
            ?,?,?,?,
            ?,?,?,
            ?,?,?,?,?,
            ?,?,?,
            ?,?,?,?,
            ?,?,?,?,?,?,
            ?,?,?,
            ?,?,?,?,?
        )
    """.trimIndent()

    private val INSERT_IGNORE_SQL = INSERT_SQL.replaceFirst("INSERT INTO log", "INSERT IGNORE INTO log")

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
            operator=?, sig=?, siginfo=?
        WHERE qsoid=?
    """.trimIndent()

    private fun java.sql.PreparedStatement.bindQso(q: Qso) {
        var i = 1
        setLong(i++, q.qsoid)
        setString(i++, q.callsign.uppercase())
        setString(i++, q.band)
        setString(i++, q.mode)
        setTimestamp(i++, Timestamp.valueOf(q.qsodate.format(JDBC_FMT)))
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
        setString(i++, q.email)
        setString(i++, q.pfx)
        setString(i++, q.state)
        setString(i++, q.cnty)
        setString(i++, q.qslvia)
        setString(i++, q.qslmsg)
        setString(i++, q.eqcall)
        setString(i++, q.qsocomplete)
        setInt(i++, q.qsorandom)
        setInt(i++, q.swl)
        setInt(i++, q.satelliteqso)
        setString(i++, q.satmode)
        setString(i++, q.satname)
        setString(i++, q.srxstring)
        setString(i++, q.stxstring)
        setString(i++, q.contactedop)
        setString(i++, q.programid)
        setString(i++, q.programversion)
        setString(i++, q.classField)
        setString(i++, q.antpath)
        setString(i++, q.antenna)
        setString(i++, q.bandrx)
        setString(i++, q.callsignurl)
        setString(i++, q.contactassociations)
        setString(i++, q.precedence)
        setString(i++, q.ownercallsign)
        setString(i++, q.sig)
        setString(i++, q.siginfo)
        setString(i++, q.mysig)
        setString(i++, q.mysiginfo)
        setString(i++, q.mycity)
        setString(i++, q.mycnty)
        setString(i++, q.mycountry)
        setString(i++, q.mypostalcode)
        setString(i++, q.mystate)
        setString(i++, q.mystreet)
        if (q.mydxcc != null) setInt(i++, q.mydxcc) else setNull(i++, Types.INTEGER)
        if (q.mylat  != null) setDouble(i++, q.mylat) else setNull(i++, Types.DECIMAL)
        if (q.mylon  != null) setDouble(i++, q.mylon) else setNull(i++, Types.DECIMAL)
        setInt(i++, q.forceinit)
        setDouble(i++, q.mufday)
        setDouble(i++, q.reliability)
        setDouble(i++, q.signaltonoiseratio)
        setInt(i++, q.sunspots)
    }

    private fun java.sql.PreparedStatement.bindQsoUpdate(q: Qso) {
        var i = 1
        setString(i++, q.callsign.uppercase())
        setString(i++, q.band)
        setString(i++, q.bandrx)
        setString(i++, q.mode)
        setTimestamp(i++, Timestamp.valueOf(q.qsodate.format(JDBC_FMT)))
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
        if (q.mylat  != null) setDouble(i++, q.mylat) else setNull(i++, Types.DECIMAL)
        if (q.mylon  != null) setDouble(i++, q.mylon) else setNull(i++, Types.DECIMAL)
        if (q.txpwr != null) setDouble(i++, q.txpwr) else setNull(i++, Types.DECIMAL)
        setString(i++, q.propmode)
        setString(i++, q.contestid)
        setString(i++, q.address)
        setString(i++, q.email)
        setString(i++, q.pfx)
        setString(i++, q.state)
        setString(i++, q.cnty)
        setString(i++, q.qslvia)
        setString(i++, q.qslmsg)
        setString(i++, q.eqcall)
        setString(i++, q.contactedop)
        setString(i++, q.srxstring)
        setString(i++, q.stxstring)
        setString(i++, q.satmode)
        setString(i++, q.satname)
        setInt(i++, q.satelliteqso)
        setString(i++, q.operator)
        setString(i++, q.sig)
        setString(i++, q.siginfo)
        setLong(i, q.qsoid)
    }

    // --- ResultSet mapping ---

    private fun ResultSet.toList(): List<Qso> {
        val result = mutableListOf<Qso>()
        while (next()) result.add(toQso())
        return result
    }

    private fun Timestamp.toLocalDT(): LocalDateTime =
        LocalDateTime.parse(this.toString().take(19), JDBC_FMT)

    private fun ResultSet.str(col: String): String = getString(col) ?: ""
    private fun ResultSet.optInt(col: String): Int? = getObject(col)?.let { (it as Number).toInt() }
    private fun ResultSet.optDouble(col: String): Double? = getObject(col)?.let { (it as Number).toDouble() }
    private fun ResultSet.optDateTime(col: String): LocalDateTime? = getTimestamp(col)?.toLocalDT()

    private fun ResultSet.toQso() = Qso(
        qsoid            = getLong("qsoid"),
        callsign         = str("callsign"),
        band             = str("band"),
        mode             = str("mode"),
        qsodate          = getTimestamp("qsodate")?.toLocalDT() ?: LocalDateTime.now(),
        address          = str("address"),
        arrlsect         = str("arrlsect"),
        age              = optDouble("age"),
        aindex           = optDouble("aindex"),
        antaz            = optDouble("antaz"),
        antel            = optDouble("antel"),
        antpath          = str("antpath"),
        antenna          = str("antenna"),
        arrlcheck        = str("arrlcheck"),
        bandrx           = str("bandrx"),
        callsignurl      = str("callsignurl"),
        classField       = str("class"),
        cnty             = str("cnty"),
        comment          = str("comment"),
        cont             = str("cont"),
        contactassociations = str("contactassociations"),
        contactedop      = str("contactedop"),
        contestid        = str("contestid"),
        country          = str("country"),
        cqzone           = optInt("cqzone"),
        distance         = optDouble("distance"),
        dxcc             = getInt("dxcc"),
        eqcall           = getString("eqcall"),
        email            = str("email"),
        forceinit        = getInt("forceinit"),
        freq             = getDouble("freq"),
        freqrx           = getDouble("freqrx"),
        gridsquare       = str("gridsquare"),
        ituzone          = optInt("ituzone"),
        kindex           = optDouble("kindex"),
        lat              = optDouble("lat"),
        lon              = optDouble("lon"),
        maxbursts        = optDouble("maxbursts"),
        msshower         = str("msshower"),
        myassociations   = str("myassociations"),
        mydxcc           = optInt("mydxcc"),
        mylat            = optDouble("mylat"),
        mylon            = optDouble("mylon"),
        mycity           = str("mycity"),
        mycnty           = str("mycnty"),
        mycountry        = str("mycountry"),
        mycqzone         = optInt("mycqzone"),
        mygridsquare     = str("mygridsquare"),
        myituzone        = optInt("myituZone"),
        myname           = str("myname"),
        mypostalcode     = str("mypostalcode"),
        myrig            = str("myrig"),
        mysig            = str("mysig"),
        mysiginfo        = str("mysiginfo"),
        mystate          = str("mystate"),
        mystreet         = str("mystreet"),
        name             = str("name"),
        notes            = str("notes"),
        nrbursts         = optDouble("nrbursts"),
        nrpings          = optDouble("nrpings"),
        operator         = str("operator"),
        ownercallsign    = str("ownercallsign"),
        pfx              = str("pfx"),
        precedence       = str("precedence"),
        programid        = str("programid"),
        programversion   = str("programversion"),
        propmode         = str("propmode"),
        qslmsg           = str("qslmsg"),
        qslvia           = str("qslvia"),
        qsocomplete      = str("qsocomplete"),
        qsoenddate       = optDateTime("qsoenddate"),
        qsorandom        = getInt("qsorandom"),
        qth              = str("qth"),
        rstrcvd          = str("rstrcvd"),
        rstsent          = str("rstsent"),
        rxpwr            = optDouble("rxpwr"),
        satelliteqso     = getInt("satelliteqso"),
        satmode          = str("satmode"),
        satname          = str("satname"),
        sfi              = optDouble("sfi"),
        sig              = str("sig"),
        siginfo          = str("siginfo"),
        stationcallsign  = str("stationcallsign"),
        srx              = optDouble("srx"),
        srxstring        = str("srxstring"),
        state            = str("state"),
        stx              = optDouble("stx"),
        stxstring        = str("stxstring"),
        swl              = getInt("swl"),
        txpwr            = optDouble("txpwr"),
        mufday           = getDouble("mufday"),
        reliability      = getDouble("reliability"),
        signaltonoiseratio = getDouble("signaltonoiseratio"),
        sunspots         = getInt("sunspots")
    )
}
