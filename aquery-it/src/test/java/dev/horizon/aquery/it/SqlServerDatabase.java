package dev.horizon.aquery.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.mssqlserver.MSSQLServerContainer;

public final class SqlServerDatabase extends Database {

  private static final MSSQLServerContainer CONTAINER = new MSSQLServerContainer(
      "mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
  private static final AtomicInteger DATABASES = new AtomicInteger();

  private final String url;

  public SqlServerDatabase() throws SQLException {
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
    url = CONTAINER.getJdbcUrl() + ";databaseName=" + name;
  }

  @Override
  public Connection connect() throws SQLException {
    return DriverManager.getConnection(url, CONTAINER.getUsername(), CONTAINER.getPassword());
  }

  @Override
  public String booleanType() {
    return "BIT";
  }

  @Override
  public String timestampType() {
    return "DATETIMEOFFSET";
  }

  @Override
  public String uuidType() {
    return "UNIQUEIDENTIFIER";
  }

  @Override
  public boolean likeIgnoresCase() {
    return true;
  }

  @Override
  public boolean equalsIgnoresCase() {
    return true;
  }
}
