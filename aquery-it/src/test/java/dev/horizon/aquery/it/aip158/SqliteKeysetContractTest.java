package dev.horizon.aquery.it.aip158;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.SqliteDatabase;
import java.sql.SQLException;

class SqliteKeysetContractTest extends KeysetContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new SqliteDatabase();
  }
}
