package dev.horizon.aquery.it.aip132;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.SqlServerDatabase;
import java.sql.SQLException;

class SqlServerOrderByContractTest extends OrderByContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new SqlServerDatabase();
  }
}
