package dev.horizon.aquery.it.aip132;

import dev.horizon.aquery.it.Database;
import dev.horizon.aquery.it.PostgresDatabase;
import java.sql.SQLException;

class PostgresOrderByContractTest extends OrderByContractTest {

  @Override
  protected Database startDatabase() throws SQLException {
    return new PostgresDatabase();
  }
}
