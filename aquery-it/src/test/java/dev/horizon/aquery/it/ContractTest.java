package dev.horizon.aquery.it;

import dev.horizon.aquery.aip160.DatabaseTable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public abstract class ContractTest {

  protected Database database;
  protected Connection connection;
  protected Items items;
  protected DatabaseTable table;

  protected abstract Database startDatabase() throws SQLException;

  @BeforeAll
  protected void createItems() throws SQLException {
    database = startDatabase();
    connection = database.connect();
    items = new Items(database);
    items.create(connection);
    table = items.table();
  }

  @AfterAll
  protected void stopDatabase() throws SQLException {
    if (connection != null) {
      connection.close();
    }
    if (database != null) {
      database.close();
    }
  }

  protected final List<Long> ids(String sql, List<Object> values) {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int i = 0; i < values.size(); i++) {
        database.bind(statement, i + 1, values.get(i));
      }
      List<Long> ids = new ArrayList<>();
      try (ResultSet rows = statement.executeQuery()) {
        while (rows.next()) {
          ids.add(rows.getLong(1));
        }
      }
      return ids;
    } catch (SQLException refused) {
      throw new AssertionError("the engine refused the SQL: " + sql, refused);
    }
  }
}
