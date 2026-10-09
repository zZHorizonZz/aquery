package dev.horizon.aquery.it.aip158;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.H2Database;
import java.sql.SQLException;

class H2KeysetContractTest extends KeysetContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new H2Database();
  }
}
