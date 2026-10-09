package dev.horizon.aquery.it.aip160;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.MySqlDatabase;
import java.sql.SQLException;

class MySqlFilterContractTest extends FilterContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new MySqlDatabase();
  }
}
