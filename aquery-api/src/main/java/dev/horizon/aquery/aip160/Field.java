package dev.horizon.aquery.aip160;

import dev.horizon.aquery.aip132.FieldPath;
import java.util.Objects;

/**
 * The schema of a field. A filter can refer to the field, an order_by clause can sort by it, and a read mask can
 * select it.
 *
 * <p>
 * A field is not always a column of the table. The field {@code username} can be the column {@code username}.
 * Another field can come from several columns, or from one column and the clock. Thus a field declares a
 * {@link FieldBackend} that writes its SQL, not an enum constant for a generator.
 *
 * <p>
 * Make a field with a {@link Builder}. Then tell what clients can do with the field:
 *
 * <ul>
 * <li>{@code filterable}: the field can be in a filter restriction, as in {@code username = "dan"}.
 * <li>{@code filterableImplicitly}: a bare value in a filter, as in {@code prod}, matches this field.
 * <li>{@code sortable}: an order_by clause can sort by this field.
 * <li>{@code readable}: a read mask can select this field, as AIP-157 specifies.
 * </ul>
 *
 * <p>
 * A field that you can filter implicitly is also filterable. A client cannot name a field without one of these
 * declarations. Thus a column that the API must not show, for example a password hash, stays hidden.
 *
 * <p>
 * The field compares these declarations with its backend when the server makes the schema. If the backend
 * cannot do what the field declares, the field throws {@link IllegalArgumentException}. For example, a
 * {@link RepeatedStringColumn} cannot be sortable. Thus the server finds this error at startup, not in a
 * request.
 *
 * @see <a href=
 *      "https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/datamodel.go">LUCI
 *      aip160: datamodel.go (Field)</a>
 */
public record Field(FieldPath fieldPath, boolean sortable, boolean filterable, boolean implicitFilter, boolean readable,
    FieldBackend backend) {

  public Field {
    Objects.requireNonNull(fieldPath, "fieldPath");
    Objects.requireNonNull(backend, "backend");
    if (implicitFilter && !filterable) {
      throw new IllegalArgumentException("field " + fieldPath + " is filterable implicitly, so it must be filterable");
    }
    if (filterable && backend.operators().isEmpty()) {
      throw new IllegalArgumentException(
          "field " + fieldPath + " is filterable, but its " + backend.typeName() + " backend supports no operators");
    }
    if (implicitFilter && !backend.supportsImplicitRestrictions()) {
      throw new IllegalArgumentException("field " + fieldPath + " is filterable implicitly, but its " + backend.typeName()
          + " backend does not match bare values");
    }
    if (sortable && !backend.supportsSorting()) {
      throw new IllegalArgumentException(
          "field " + fieldPath + " is sortable, but its " + backend.typeName() + " backend does not support sorting");
    }
    if (readable && !backend.supportsReading()) {
      throw new IllegalArgumentException(
          "field " + fieldPath + " is readable, but its " + backend.typeName() + " backend does not support reading");
    }
  }

  /**
   * Makes a {@link Field} step by step.
   *
   * <p>
   * Give the path as segments. The traversal operator ({@code .}) is between the segments, as AIP-161 specifies.
   * For example, the segments {@code "metrics", "some-metric", "value"} are the path
   * {@code metrics.`some-metric`.value}.
   */
  public static final class Builder {

    private final FieldPath fieldPath;
    private boolean sortable;
    private boolean filterable;
    private boolean implicitFilter;
    private boolean readable;
    private FieldBackend backend;

    public Builder(String... segments) {
      this.fieldPath = new FieldPath(segments);
    }

    /**
     * Sets the backend that filters and sorts this field in the database.
     *
     * @param backend the backend of the field
     * @return this builder
     */
    public Builder backend(FieldBackend backend) {
      this.backend = backend;
      return this;
    }

    /**
     * Lets an order_by clause sort by the field.
     *
     * @return this builder
     */
    public Builder sortable() {
      this.sortable = true;
      return this;
    }

    /**
     * Lets a filter restriction use the field.
     *
     * @return this builder
     */
    public Builder filterable() {
      this.filterable = true;
      return this;
    }

    /**
     * Lets a bare value in a filter match the field, as in {@code filter=prod}. The field is then also filterable.
     *
     * @return this builder
     */
    public Builder filterableImplicitly() {
      this.filterable = true;
      this.implicitFilter = true;
      return this;
    }

    /**
     * Lets a read mask select the field.
     *
     * @return this builder
     */
    public Builder readable() {
      this.readable = true;
      return this;
    }

    /**
     * Makes the field.
     *
     * @return the field
     * @throws IllegalStateException if no backend was set
     * @throws IllegalArgumentException if the backend cannot do what the field declares
     */
    public Field build() {
      if (backend == null) {
        throw new IllegalStateException("field " + fieldPath + " needs a backend");
      }
      return new Field(fieldPath, sortable, filterable, implicitFilter, readable, backend);
    }
  }
}
