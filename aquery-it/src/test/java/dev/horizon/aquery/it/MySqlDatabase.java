package dev.horizon.aquery.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.testcontainers.mysql.MySQLContainer;

public final class MySqlDatabase extends Database {

  private static final MySQLContainer CONTAINER = new MySQLContainer("mysql:8.4");
  private static final AtomicInteger DATABASES = new AtomicInteger();
  private static final String USER = "root";

  private final String url;

  public MySqlDatabase() throws SQLException {
    synchronized (CONTAINER) {
      if (!CONTAINER.isRunning()) {
        CONTAINER.start();
      }
    }
    String name = "aquery_" + DATABASES.incrementAndGet();
    try (Connection admin = DriverManager.getConnection(CONTAINER.getJdbcUrl(), USER, CONTAINER.getPassword());
        Statement statement = admin.createStatement()) {
      statement.execute("CREATE DATABASE " + name);
    }
    url = "jdbc:mysql://" + CONTAINER.getHost() + ":" + CONTAINER.getMappedPort(MySQLContainer.MYSQL_PORT) + "/" + name;
  }

  @Override
  public Connection connect() throws SQLException {
    return DriverManager.getConnection(url, USER, CONTAINER.getPassword());
  }

  @Override
  public String booleanType() {
    return "BOOLEAN";
  }

  @Override
  public String timestampType() {
    return "TIMESTAMP(6)";
  }

  @Override
  public String uuidType() {
    return "CHAR(36)";
  }

  @Override
  public boolean likeIgnoresCase() {
    return true;
  }

  @Override
  public boolean equalsIgnoresCase() {
    return true;
  }

  @Override
  public void bind(PreparedStatement statement, int index, Object value) throws SQLException {
    statement.setObject(index, value instanceof UUID uuid ? uuid.toString() : value);
  }

  @Override
  public <T> T read(ResultSet row, String column, Class<T> type) throws SQLException {
    if (type == UUID.class) {
      return type.cast(UUID.fromString(row.getString(column)));
    }
    return super.read(row, column, type);
  }
}
