package dev.horizon.aquery.it.aip160;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.SqliteDatabase;
import java.sql.SQLException;

class SqliteFilterContractTest extends FilterContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new SqliteDatabase();
  }
}
