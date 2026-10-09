package dev.horizon.aquery.it.aip158;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.PostgresDatabase;
import java.sql.SQLException;

class PostgresKeysetContractTest extends KeysetContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new PostgresDatabase();
  }
}
