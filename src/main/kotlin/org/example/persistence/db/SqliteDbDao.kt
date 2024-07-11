package org.example.persistence.db

import arrow.core.Either
import arrow.core.Option
import arrow.core.flatMap
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import org.example.persistence.db.domain.SqlDbDao

class SqliteDbDao(private val connection: Connection) : SqlDbDao {
  override fun query(query: String): Option<ResultSet> {
    return Either.catch { connection.createStatement() }
        .onRight { it.queryTimeout = DEFAULT_TIMEOUT }
        .onRight { LOGGER.trace { "Executing query: $query" } }
        .flatMap { Either.catch { it.executeQuery(query) } }
        .onLeft { LOGGER.error(it) { "Database query failed." } }
        .getOrNone()
  }

  override fun update(update: String): Option<Int> {
    return Either.catch { connection.createStatement() }
        .onRight { it.queryTimeout = DEFAULT_TIMEOUT }
        .onRight { LOGGER.trace { "Executing update: $update" } }
        .flatMap { Either.catch { it.executeUpdate(update) } }
        .onLeft { LOGGER.error(it) { "Database update failed." } }
        .getOrNone()
  }

  override fun cleanup() {
    connection.close()
  }

  companion object {
    const val DEFAULT_TIMEOUT = 30

    private val LOGGER = KotlinLogging.logger {}

    fun mk(uri: URI): Option<SqliteDbDao> {
      return Either.catch<Connection> { DriverManager.getConnection(uri.toString()) }
          .onLeft { LOGGER.error(it) { "Unable to connect to the database $uri" } }
          .map { SqliteDbDao(it) }
          .getOrNone()
    }
  }
}
