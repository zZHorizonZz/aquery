package dev.horizon.aquery.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.postgresql.PostgreSQLContainer;

public final class PostgresDatabase extends Database {

  private static final PostgreSQLContainer CONTAINER = new PostgreSQLContainer("postgres:17-alpine");
  private static final AtomicInteger DATABASES = new AtomicInteger();

  private final String url;

  public PostgresDatabase() throws SQLException {
    synchronized (CONTAINER) {
      if (!CONTAINER.isRunning()) {
        CONTAINER.start();
      }
    }
    String name = "aquery_" + DATABASES.incrementAndGet();
    try (Connection admin = DriverManager.getConnection(CONTAINER.getJdbcUrl(), CONTAINER.getUsername(),
        CONTAINER.getPassword()); Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    url = "jdbc:postgresql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
        + "/" + name;
  }

  @Override
  public Connection connect() throws SQLException {
    return DriverManager.getConnection(url, CONTAINER.getUsername(), CONTAINER.getPassword());
  }

  @Override
  public String booleanType() {
    return "BOOLEAN";
  }

  @Override
  public String timestampType() {
    return "TIMESTAMP WITH TIME ZONE";
  }

  @Override
  public String uuidType() {
    return "UUID";
  }

  @Override
  public String stringArrayType() {
    return "VARCHAR(50)[]";
  }

  @Override
  public String stringArrayElementType() {
    return "varchar";
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
