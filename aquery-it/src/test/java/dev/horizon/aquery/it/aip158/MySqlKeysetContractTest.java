package dev.horizon.aquery.it.aip158;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.MySqlDatabase;
import java.sql.SQLException;

class MySqlKeysetContractTest extends KeysetContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new MySqlDatabase();
  }
}
