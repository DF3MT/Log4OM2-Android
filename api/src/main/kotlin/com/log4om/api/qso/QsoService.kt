package com.log4om.api.qso

import com.log4om.api.common.NotFoundException
import com.log4om.api.security.UserPrincipal
import com.log4om.api.tenant.TenantDbService
import org.springframework.stereotype.Service

@Service
class QsoService(
    private val tenantDb: TenantDbService,
    private val dao: QsoDao
) {
    fun list(principal: UserPrincipal, filter: LogFilterDto, limit: Int, offset: Int) =
        tenantDb.withTenantConnection(principal.tenantId) { dao.queryFiltered(it, filter, limit, offset) }

    fun count(principal: UserPrincipal, filter: LogFilterDto) =
        tenantDb.withTenantConnection(principal.tenantId) { dao.countFiltered(it, filter) }

    fun ids(principal: UserPrincipal, filter: LogFilterDto) =
        tenantDb.withTenantConnection(principal.tenantId) { dao.getFilteredIds(it, filter) }

    fun byIds(principal: UserPrincipal, ids: List<Long>) =
        tenantDb.withTenantConnection(principal.tenantId) { dao.getByIds(it, ids) }

    fun byCallsign(principal: UserPrincipal, call: String, limit: Int) =
        tenantDb.withTenantConnection(principal.tenantId) { dao.getByCallsign(it, call, limit) }

    fun get(principal: UserPrincipal, id: Long): QsoDto {
        val list = byIds(principal, listOf(id))
        return list.firstOrNull() ?: throw NotFoundException("QSO $id not found")
    }

    fun create(principal: UserPrincipal, qso: QsoDto): QsoDto {
        val id = qso.qsoid ?: System.currentTimeMillis()
        val toSave = qso.copy(qsoid = id)
        tenantDb.withTenantConnection(principal.tenantId) { dao.insert(it, toSave) }
        return get(principal, id)
    }

    fun update(principal: UserPrincipal, id: Long, qso: QsoDto): QsoDto {
        val toSave = qso.copy(qsoid = id)
        val ok = tenantDb.withTenantConnection(principal.tenantId) { dao.update(it, toSave) }
        if (!ok) throw NotFoundException("QSO $id not found")
        return get(principal, id)
    }

    fun delete(principal: UserPrincipal, id: Long) {
        val ok = tenantDb.withTenantConnection(principal.tenantId) { dao.delete(it, id) }
        if (!ok) throw NotFoundException("QSO $id not found")
    }

    fun workedDxcc(principal: UserPrincipal) =
        tenantDb.withTenantConnection(principal.tenantId) { dao.getWorkedDxccIds(it) }

    fun workedDxccBands(principal: UserPrincipal) =
        tenantDb.withTenantConnection(principal.tenantId) {
            dao.getWorkedDxccBands(it).map { (dxcc, band) -> mapOf("dxcc" to dxcc, "band" to band) }
        }

    fun bulkInsert(principal: UserPrincipal, qsos: List<QsoDto>) =
        tenantDb.withTenantConnection(principal.tenantId) { dao.bulkInsert(it, qsos) }
}
