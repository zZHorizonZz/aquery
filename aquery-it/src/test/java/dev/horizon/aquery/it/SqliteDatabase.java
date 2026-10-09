package dev.horizon.aquery.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class SqliteDatabase extends Database {

  private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'");

  private final String url = "jdbc:sqlite:file:aquery-" + UUID.randomUUID() + "?mode=memory&cache=shared";

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
    return "TEXT";
  }

  @Override
  public String uuidType() {
    return "TEXT";
  }

  @Override
  public boolean likeIgnoresCase() {
    return true;
  }

  @Override
  public boolean equalsIgnoresCase() {
    return false;
  }

  @Override
  public void bind(PreparedStatement statement, int index, Object value) throws SQLException {
    Object bound = switch (value) {
      case UUID uuid -> uuid.toString();
      case OffsetDateTime timestamp -> TIMESTAMP.format(timestamp.withOffsetSameInstant(ZoneOffset.UTC));
      default -> value;
    };
    statement.setObject(index, bound);
  }

  @Override
  public <T> T read(ResultSet row, String column, Class<T> type) throws SQLException {
    if (type == UUID.class) {
      return type.cast(UUID.fromString(row.getString(column)));
    }
    if (type == OffsetDateTime.class) {
      return type.cast(OffsetDateTime.parse(row.getString(column)));
    }
    return super.read(row, column, type);
  }
}
