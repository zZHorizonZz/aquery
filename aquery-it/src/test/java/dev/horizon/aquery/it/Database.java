package dev.horizon.aquery.it;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public abstract class Database implements AutoCloseable {

  public abstract Connection connect() throws SQLException;

  public abstract String booleanType();

  public abstract String timestampType();

  public abstract String uuidType();

  public String stringArrayType() {
    return null;
  }

  public String stringArrayElementType() {
    return "VARCHAR";
  }

  public abstract boolean likeIgnoresCase();

  public abstract boolean equalsIgnoresCase();

  public void bind(PreparedStatement statement, int index, Object value) throws SQLException {
    statement.setObject(index, value);
  }

  public <T> T read(ResultSet row, String column, Class<T> type) throws SQLException {
    return row.getObject(column, type);
  }

  @Override
  public void close() {
  }
}
