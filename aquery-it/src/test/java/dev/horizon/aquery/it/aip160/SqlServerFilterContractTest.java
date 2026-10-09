package dev.horizon.aquery.it.aip160;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.SqlServerDatabase;
import java.sql.SQLException;

class SqlServerFilterContractTest extends FilterContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new SqlServerDatabase();
  }
}
