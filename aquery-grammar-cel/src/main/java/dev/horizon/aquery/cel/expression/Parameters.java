package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Type;

import java.util.Map;
import java.util.Optional;

/**
 * The values that the caller gives with an expression, which it reads as {@code params.name}. Only their types are
 * here: one compiled expression is good for many statements, with other values each time. {@link ParameterValues}
 * gives them from the values.
 *
 * @param types the type of each value, by its name
 */
public record Parameters(Map<String, Type> types) {

  /** No values. */
  public static final Parameters NONE = new Parameters(Map.of());

  public Parameters {
    types = Map.copyOf(types);
  }

  /**
   * Finds the type of a value.
   *
   * @param name the name of the value
   * @return the type, or empty if there is no value with the name
   */
  public Optional<Type> type(String name) {
    return Optional.ofNullable(types.get(name));
  }
}
