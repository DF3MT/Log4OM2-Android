package com.log4om.android.data.db

import com.log4om.android.data.model.LogFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFilterSqlTest {

    @Test
    fun awardFiltersUseContactreferencesNotFakeColumns() {
        val sql = LogFilterSql.build(
            LogFilter(sotaRef = "DM/BW-001", cotaRef = "C-1")
        ).whereSql
        assertTrue(sql.contains("contactreferences"))
        assertFalse(sql.contains("sota_ref"))
        assertFalse(sql.contains("cota_ref"))
        assertFalse(sql.contains("pota_ref"))
        assertFalse(sql.contains("wwff_ref"))
        assertFalse("iota LIKE" in sql || sql.contains(" iota "))
    }

    @Test
    fun emptyFilterHasNoWhere() {
        assertTrue(LogFilterSql.build(LogFilter()).whereSql.isEmpty())
    }
}
