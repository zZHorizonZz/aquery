package dev.horizon.aquery.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;

public final class H2Database extends Database {

  private final String url = "jdbc:h2:mem:aquery-" + UUID.randomUUID();

  @Override
  public Connection connect() throws SQLException {
    return DriverManager.getConnection(url);
  }

  @Override
  public String booleanType() {
    return "BOOLEAN";
  }

  @Override
  public String timestampType() {
    return "TIMESTAMP(9) WITH TIME ZONE";
  }

  @Override
  public String uuidType() {
    return "UUID";
  }

  @Override
  public boolean likeIgnoresCase() {
    return false;
  }

  @Override
  public boolean equalsIgnoresCase() {
    return false;
  }
}
