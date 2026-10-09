package dev.horizon.aquery.it.aip160;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.PostgresDatabase;
import java.sql.SQLException;

class PostgresFilterContractTest extends FilterContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new PostgresDatabase();
  }
}
