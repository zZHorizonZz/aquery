package dev.horizon.aquery.it.aip132;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.SqliteDatabase;
import java.sql.SQLException;

class SqliteOrderByContractTest extends OrderByContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new SqliteDatabase();
  }
}
