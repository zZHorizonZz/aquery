package dev.horizon.aquery.it.aip132;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.H2Database;
import java.sql.SQLException;

class H2OrderByContractTest extends OrderByContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new H2Database();
  }
}
