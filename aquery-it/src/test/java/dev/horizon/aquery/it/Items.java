package dev.horizon.aquery.it;

import dev.horizon.aquery.aip160.Args;
import dev.horizon.aquery.aip160.BoolColumn;
import dev.horizon.aquery.aip160.DatabaseTable;
import dev.horizon.aquery.aip160.DurationColumn;
import dev.horizon.aquery.aip160.EnumColumn;
import dev.horizon.aquery.aip160.EnumColumn.Storage;
import dev.horizon.aquery.aip160.Field;
import dev.horizon.aquery.aip160.IntegerColumn;
import dev.horizon.aquery.aip160.KeyValueColumn;
import dev.horizon.aquery.aip160.KeyValueColumn.ChildTable;
import dev.horizon.aquery.aip160.KeyValueColumn.Representation;
import dev.horizon.aquery.aip160.RepeatedStringColumn;
import dev.horizon.aquery.aip160.StringColumn;
import dev.horizon.aquery.aip160.TimestampColumn;
import dev.horizon.aquery.aip160.UuidColumn;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;
import java.util.UUID;

public final class Items {

  public record Column(String name, Class<?> type) {
  }

  private static final Args.EnumDefinition STATUS = new Args.EnumDefinition("Status", Map.of("ACTIVE", 1, "INACTIVE", 2), 0);

  private static final SequencedMap<String, Column> SORT_COLUMNS = new LinkedHashMap<>();

  static {
    SORT_COLUMNS.put("id", new Column("id", Long.class));
    SORT_COLUMNS.put("name", new Column("name", String.class));
    SORT_COLUMNS.put("flag", new Column("flag", Boolean.class));
    SORT_COLUMNS.put("count", new Column("item_count", Long.class));
    SORT_COLUMNS.put("age", new Column("age", Long.class));
    SORT_COLUMNS.put("create_time", new Column("created", OffsetDateTime.class));
    SORT_COLUMNS.put("status", new Column("status", Integer.class));
    SORT_COLUMNS.put("status_name", new Column("status_name", String.class));
    SORT_COLUMNS.put("uid", new Column("uid", UUID.class));
  }

  private record Item(long id, String name, boolean flag, long count, Duration age, OffsetDateTime created, int status,
      UUID uid, List<String> tags, SequencedMap<String, String> labels) {

    String statusName() {
      return status == 1 ? "ACTIVE" : "INACTIVE";
    }
  }

  private static final List<Item> ITEMS = List.of(
      new Item(1, "alpha", true, 10, Duration.ofSeconds(1), OffsetDateTime.parse("2024-01-01T00:00:00Z"), 1,
          UUID.fromString("00000000-0000-0000-0000-000000000001"), List.of("red", "blue"),
          labels("env", "prod", "team", "core")),
      new Item(2, "beta", false, 20, Duration.ofSeconds(90), OffsetDateTime.parse("2024-12-31T23:00:00Z"), 2,
          UUID.fromString("00000000-0000-0000-0000-000000000002"), List.of("green"), labels("env", "dev")),
      new Item(3, "Gamma_x", true, 30, Duration.ZERO, OffsetDateTime.parse("2025-01-01T00:00:00+02:00"), 1,
          UUID.fromString("00000000-0000-0000-0000-000000000003"), List.of(), labels()),
      new Item(4, "100% delta", false, -5, Duration.ofHours(2), OffsetDateTime.parse("2023-03-15T08:30:00-05:00"), 2,
          UUID.fromString("00000000-0000-0000-0000-000000000004"), List.of("red"), labels("team", "web")),
      new Item(5, "a!b", true, 0, Duration.ofMillis(1500), OffsetDateTime.parse("2024-01-01T00:00:00.5Z"), 1,
          UUID.fromString("00000000-0000-0000-0000-000000000005"), List.of(), labels("env", "pro_d")));

  private final Database database;

  public Items(Database database) {
    this.database = database;
  }

  public boolean hasArrays() {
    return database.stringArrayType() != null;
  }

  public int size() {
    return ITEMS.size();
  }

  public SequencedMap<String, Column> sortColumns() {
    return Collections.unmodifiableSequencedMap(SORT_COLUMNS);
  }

  void create(Connection connection) throws SQLException {
    String arrays = hasArrays()
        ? ", tags " + database.stringArrayType() + ", attrs " + database.stringArrayType()
        : "";
    try (Statement statement = connection.createStatement()) {
      statement.execute("CREATE TABLE item (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(100) NOT NULL, flag "
          + database.booleanType() + " NOT NULL, item_count BIGINT NOT NULL, age BIGINT NOT NULL, created "
          + database.timestampType() + " NOT NULL, status INTEGER NOT NULL, status_name VARCHAR(20) NOT NULL, uid "
          + database.uuidType() + " NOT NULL" + arrays + ")");
      statement.execute(
          "CREATE TABLE item_label (item_id BIGINT NOT NULL, label_key VARCHAR(50) NOT NULL, label_value VARCHAR(50) NOT NULL)");
    }
    String insert = hasArrays()
        ? "INSERT INTO item VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        : "INSERT INTO item VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
    try (PreparedStatement items = connection.prepareStatement(insert);
        PreparedStatement labels = connection.prepareStatement("INSERT INTO item_label VALUES (?, ?, ?)")) {
      for (Item item : ITEMS) {
        database.bind(items, 1, item.id());
        database.bind(items, 2, item.name());
        database.bind(items, 3, item.flag());
        database.bind(items, 4, item.count());
        database.bind(items, 5, item.age().toNanos());
        database.bind(items, 6, item.created());
        database.bind(items, 7, item.status());
        database.bind(items, 8, item.statusName());
        database.bind(items, 9, item.uid());
        if (hasArrays()) {
          List<String> attrs = new ArrayList<>();
          item.labels().forEach((key, value) -> attrs.add(key + ":" + value));
          items.setArray(10, connection.createArrayOf(database.stringArrayElementType(), item.tags().toArray()));
          items.setArray(11, connection.createArrayOf(database.stringArrayElementType(), attrs.toArray()));
        }
        items.executeUpdate();
        for (Map.Entry<String, String> label : item.labels().entrySet()) {
          database.bind(labels, 1, item.id());
          database.bind(labels, 2, label.getKey());
          database.bind(labels, 3, label.getValue());
          labels.executeUpdate();
        }
      }
    }
  }

  public DatabaseTable table() {
    List<Field> fields = new ArrayList<>(List.of(
        new Field.Builder("id").backend(new IntegerColumn("id")).filterable().sortable().build(),
        new Field.Builder("name").backend(new StringColumn("name")).filterableImplicitly().sortable().build(),
        new Field.Builder("flag").backend(new BoolColumn("flag")).filterable().sortable().build(),
        new Field.Builder("count").backend(new IntegerColumn("item_count")).filterable().sortable().build(),
        new Field.Builder("age").backend(new DurationColumn("age")).filterable().sortable().build(),
        new Field.Builder("create_time").backend(new TimestampColumn("created")).filterable().sortable().build(),
        new Field.Builder("status").backend(new EnumColumn("status", STATUS)).filterable().sortable().build(),
        new Field.Builder("status_name").backend(new EnumColumn("status_name", STATUS, Storage.NAME)).filterable()
            .sortable().build(),
        new Field.Builder("uid").backend(new UuidColumn("uid")).filterable().sortable().build(),
        new Field.Builder("labels")
            .backend(new KeyValueColumn(new ChildTable("item_label", "item_id", "label_key", "label_value", "id")))
            .filterable()
            .build()));
    if (hasArrays()) {
      fields.add(new Field.Builder("tags").backend(new RepeatedStringColumn("tags")).filterable().build());
      fields.add(new Field.Builder("attrs").backend(new KeyValueColumn("attrs", Representation.STRING_ARRAY)).filterable()
          .build());
    }
    return new DatabaseTable(fields.toArray(Field[]::new));
  }

  private static SequencedMap<String, String> labels(String... keysAndValues) {
    SequencedMap<String, String> labels = new LinkedHashMap<>();
    for (int i = 0; i < keysAndValues.length; i += 2) {
      labels.put(keysAndValues[i], keysAndValues[i + 1]);
    }
    return labels;
  }
}
