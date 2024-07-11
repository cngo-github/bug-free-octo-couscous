package org.example.persistence.db.domain

import arrow.core.Option
import java.sql.ResultSet

interface SqlDbDao {
  fun query(query: String): Option<ResultSet>

  fun update(update: String): Option<Int>

  fun cleanup()
}
