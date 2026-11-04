package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Scope;
import dev.horizon.aquery.cel.internal.CelTree;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The names that the text of an expression can read, as CEL calls its declarations.
 *
 * <ul>
 * <li>The fields and the edges of its row, by their names, as in {@code total_cents}.
 * <li>Other rows, by a name, as the {@code new} and the {@code old} row of a trigger. The text reads the fields of
 * such a row as {@code new.total_cents}, and each is a {@link Expression.FieldReference} of the
 * {@link Expression.Row.Named} row.
 * <li>The parameters, which the caller gives with the expression, as {@code params.min}.
 * </ul>
 *
 * <pre>{@code
 * CelEnvironment trigger = CelEnvironment.of(orders).with("new", orders).with("old", orders);
 * }</pre>
 *
 * <p>
 * An environment cannot change.
 */
public final class CelEnvironment {

  private final Scope scope;
  private final Map<String, Scope> rows;
  private final Parameters parameters;

  private CelEnvironment(Scope scope, Map<String, Scope> rows, Parameters parameters) {
    this.scope = scope;
    this.rows = Map.copyOf(rows);
    this.parameters = parameters;
  }

  /**
   * Gives the environment of the fields of a scope, without other rows and without parameters.
   *
   * @param scope the scope
   * @return the environment
   */
  public static CelEnvironment of(Scope scope) {
    return new CelEnvironment(Objects.requireNonNull(scope, "scope"), Map.of(), Parameters.NONE);
  }

  /**
   * Gives this environment with one more row.
   *
   * @param name the name of the row, for example {@code new}
   * @param row the fields of the row
   * @return the environment
   * @throws IllegalArgumentException if the scope has a field or an edge with the name, or if aquery reserves the name,
   * as it does {@code params}
   */
  public CelEnvironment with(String name, Scope row) {
    if (name.equals(Checker.PARAMS) || name.equals(CelTree.RECORD)) {
      throw new IllegalArgumentException("the row " + name + " has a name that aquery reserves");
    }
    if (scope.field(name).isPresent() || scope.edge(name).isPresent()) {
      throw new IllegalArgumentException("the row " + name + " has the name of a field or an edge of " + scope.name());
    }
    Map<String, Scope> all = new LinkedHashMap<>(rows);
    all.put(name, Objects.requireNonNull(row, "row"));
    return new CelEnvironment(scope, all, parameters);
  }

  /**
   * Gives this environment with other parameters.
   *
   * @param parameters the parameters
   * @return the environment
   */
  public CelEnvironment with(Parameters parameters) {
    return new CelEnvironment(scope, rows, Objects.requireNonNull(parameters, "parameters"));
  }

  /**
   * Gives the scope of the row of the expression.
   *
   * @return the scope
   */
  public Scope scope() {
    return scope;
  }

  /**
   * Gives the other rows.
   *
   * @return the scope of each row, by its name
   */
  public Map<String, Scope> rows() {
    return rows;
  }

  /**
   * Finds the scope of another row.
   *
   * @param name the name of the row
   * @return the scope, or empty if there is no row with the name
   */
  public Optional<Scope> row(String name) {
    return Optional.ofNullable(rows.get(name));
  }

  /**
   * Gives the parameters.
   *
   * @return the parameters
   */
  public Parameters parameters() {
    return parameters;
  }
}
