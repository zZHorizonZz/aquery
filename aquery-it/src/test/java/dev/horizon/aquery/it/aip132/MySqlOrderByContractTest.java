package dev.horizon.aquery.it.aip132;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.MySqlDatabase;
import java.sql.SQLException;

class MySqlOrderByContractTest extends OrderByContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new MySqlDatabase();
  }
}
