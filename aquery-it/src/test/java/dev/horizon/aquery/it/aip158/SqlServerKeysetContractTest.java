package dev.horizon.aquery.it.aip158;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.SqlServerDatabase;
import java.sql.SQLException;

class SqlServerKeysetContractTest extends KeysetContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new SqlServerDatabase();
  }
}
