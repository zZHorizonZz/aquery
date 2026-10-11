package dev.horizon.aquery.cel;

import dev.horizon.aquery.common.Identifiers;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The fields of a table, as an expression sees them.
 *
 * <p>
 * An expression names the fields of one row by their names, as in {@code state == 'OPEN'}. The scope tells the
 * compiler the type of each field, and tells where the value is stored: in its own column, or in a JSON column.
 *
 * <p>
 * The server declares the scope. A client never names a column. Make one scope for each table and keep it:
 *
 * <pre>{@code
 * Scope orders = Scope.builder("shop.v1.Order")
 *     .column("state", Type.Scalar.STRING, "state")
 *     .column("total_cents", Type.Scalar.INT, "total_cents")
 *     .nullable("expire_time", Type.Scalar.TIMESTAMP, "expire_time")
 *     .json("address", address, "address", true)
 *     .edge("customer", false, "customer_id", "id", () -> customers)
 *     .build();
 * }</pre>
 *
 * <p>
 * An edge names the related rows of another table, as in {@code customer.name} or
 * {@code items.exists(i, i.quantity > 1)}. Scopes of tables that refer to each other can be made in any order, since
 * an edge finds the scope of its table only when an expression uses it.
 */
public final class Scope {

  private final String name;
  private final List<Field> fields;
  private final List<Edge> edges;
  private final Map<String, Field> byName;
  private final Map<String, Edge> edgesByName;

  private Scope(String name, List<Field> fields, List<Edge> edges) {
    this.name = name;
    this.fields = List.copyOf(fields);
    this.edges = List.copyOf(edges);
    this.byName = new LinkedHashMap<>();
    for (Field field : this.fields) {
      byName.put(field.name(), field);
    }
    this.edgesByName = new LinkedHashMap<>();
    for (Edge edge : this.edges) {
      edgesByName.put(edge.name(), edge);
    }
  }

  /**
   * Starts a scope.
   *
   * @param name the full name of the record, for example {@code shop.v1.Order}
   * @return the builder of the scope
   */
  public static Builder builder(String name) {
    return new Builder(name);
  }

  /**
   * Gives the full name of the record.
   *
   * @return the name, for example {@code shop.v1.Order}
   */
  public String name() {
    return name;
  }

  /**
   * Gives the fields.
   *
   * @return the fields, in the sequence of their declaration. The list cannot change
   */
  public List<Field> fields() {
    return fields;
  }

  /**
   * Finds a field by its name.
   *
   * @param name the name of the field
   * @return the field, or empty if the scope has no field with the name
   */
  public Optional<Field> field(String name) {
    return Optional.ofNullable(byName.get(name));
  }

  /**
   * Gives the edges.
   *
   * @return the edges, in the sequence of their declaration. The list cannot change
   */
  public List<Edge> edges() {
    return edges;
  }

  /**
   * Finds an edge by its name.
   *
   * @param name the name of the edge
   * @return the edge, or empty if the scope has no edge with the name
   */
  public Optional<Edge> edge(String name) {
    return Optional.ofNullable(edgesByName.get(name));
  }

  /**
   * How the value of a field is stored.
   *
   * <ul>
   * <li>{@link #VALUE}: in a column of the type of the field.
   * <li>{@link #UUID}: a string that is a UUID, in a column of the UUID type of the database.
   * <li>{@link #JSON}: a struct, a list or a map, as JSON text in one column. A path such as {@code address.country}
   * reads a value inside it.
   * </ul>
   */
  public enum Storage {
    VALUE,
    UUID,
    JSON
  }

  /**
   * A field of the scope.
   *
   * @param name the name of the field in an expression, for example {@code total_cents}
   * @param type the type of the field
   * @param column the name of the column. Letters, digits and underscores.
   * @param storage how the value is stored
   * @param nullable true if the column can be NULL. An expression reads NULL as the default value of the type, and
   * {@code has()} tells if the column has a value.
   */
  public record Field(String name, Type type, String column, Storage storage, boolean nullable) {

    public Field {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(storage, "storage");
      if (!Identifiers.isIdentifier(column)) {
        throw new IllegalArgumentException("a column name has letters, digits and '_', was '" + column + "'");
      }
      boolean collection = type instanceof Type.Struct || type instanceof Type.ListOf || type instanceof Type.MapOf;
      if (collection != (storage == Storage.JSON)) {
        throw new IllegalArgumentException("the field " + name + " of type " + type + " cannot be stored as " + storage);
      }
      if (storage == Storage.UUID && type != Type.Scalar.STRING) {
        throw new IllegalArgumentException("the field " + name + " is a UUID, so its type must be a string");
      }
    }
  }

  /**
   * An edge of the scope: a name for the rows of another table that relate to a row.
   *
   * <p>
   * The related rows are the rows of the other table whose column {@link #targetColumn()} has the value of the column
   * {@link #column()} of the row. An edge to one row reads as a struct, as in {@code customer.name}, and an edge to
   * many rows as a list, as in {@code items.exists(i, i.quantity > 1)}.
   */
  public static final class Edge {

    private final String name;
    private final boolean many;
    private final String column;
    private final String targetColumn;
    private final Supplier<Scope> target;

    private Edge(String name, boolean many, String column, String targetColumn, Supplier<Scope> target) {
      this.name = Objects.requireNonNull(name, "name");
      this.many = many;
      if (!Identifiers.isIdentifier(column) || !Identifiers.isIdentifier(targetColumn)) {
        throw new IllegalArgumentException("a column name has letters, digits and '_', was '" + column + "' and '"
            + targetColumn + "'");
      }
      this.column = column;
      this.targetColumn = targetColumn;
      this.target = Objects.requireNonNull(target, "target");
    }

    /**
     * Gives the name of the edge in an expression.
     *
     * @return the name, for example {@code customer}
     */
    public String name() {
      return name;
    }

    /**
     * Tells if the edge has many related rows.
     *
     * @return true for many rows, false for one row or none
     */
    public boolean many() {
      return many;
    }

    /**
     * Gives the column of this table whose value relates the rows.
     *
     * @return the name of the column, for example {@code customer_id}
     */
    public String column() {
      return column;
    }

    /**
     * Gives the column of the related table whose value relates the rows.
     *
     * @return the name of the column, for example {@code id}
     */
    public String targetColumn() {
      return targetColumn;
    }

    /**
     * Gives the scope of the related table.
     *
     * @return the scope
     */
    public Scope target() {
      return Objects.requireNonNull(target.get(), "the scope of the edge " + name);
    }

    @Override
    public String toString() {
      return "Edge[" + name + (many ? ", many" : "") + ", " + column + " -> " + targetColumn + "]";
    }
  }

  /** The builder of a {@link Scope}. */
  public static final class Builder {

    private final String name;
    private final List<Field> fields = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();

    private Builder(String name) {
      this.name = Objects.requireNonNull(name, "name");
    }

    /**
     * Adds a field in its own column that always has a value.
     *
     * @param name the name of the field
     * @param type the type of the field, a {@link Type.Scalar}
     * @param column the name of the column
     * @return this builder
     */
    public Builder column(String name, Type type, String column) {
      return field(new Field(name, type, column, Storage.VALUE, false));
    }

    /**
     * Adds a field in its own column that can be NULL.
     *
     * @param name the name of the field
     * @param type the type of the field, a {@link Type.Scalar}
     * @param column the name of the column
     * @return this builder
     */
    public Builder nullable(String name, Type type, String column) {
      return field(new Field(name, type, column, Storage.VALUE, true));
    }

    /**
     * Adds a string field that is a UUID, in a column of the UUID type of the database.
     *
     * @param name the name of the field
     * @param column the name of the column
     * @param nullable true if the column can be NULL
     * @return this builder
     */
    public Builder uuid(String name, String column, boolean nullable) {
      return field(new Field(name, Type.Scalar.STRING, column, Storage.UUID, nullable));
    }

    /**
     * Adds a struct, list or map field, stored as JSON in one column.
     *
     * @param name the name of the field
     * @param type the type of the field
     * @param column the name of the column
     * @param nullable true if the column can be NULL, for example a message field that is not set
     * @return this builder
     */
    public Builder json(String name, Type type, String column, boolean nullable) {
      return field(new Field(name, type, column, Storage.JSON, nullable));
    }

    /**
     * Adds a field.
     *
     * @param field the field
     * @return this builder
     * @throws IllegalArgumentException if the scope has a field with the same name
     */
    public Builder field(Field field) {
      requireNew(field.name());
      fields.add(field);
      return this;
    }

    /**
     * Adds an edge.
     *
     * @param name the name of the edge
     * @param many true if the edge has many related rows
     * @param column the column of this table whose value relates the rows
     * @param targetColumn the column of the related table whose value relates the rows
     * @param target gives the scope of the related table, when an expression first uses the edge
     * @return this builder
     * @throws IllegalArgumentException if the scope has a field or an edge with the same name
     */
    public Builder edge(String name, boolean many, String column, String targetColumn, Supplier<Scope> target) {
      requireNew(name);
      edges.add(new Edge(name, many, column, targetColumn, target));
      return this;
    }

    private void requireNew(String field) {
      if (fields.stream().anyMatch(other -> other.name().equals(field))
          || edges.stream().anyMatch(other -> other.name().equals(field))) {
        throw new IllegalArgumentException("the scope " + name + " has two fields named " + field);
      }
    }

    /**
     * Makes the scope.
     *
     * @return the scope
     */
    public Scope build() {
      return new Scope(name, fields, edges);
    }
  }
}
