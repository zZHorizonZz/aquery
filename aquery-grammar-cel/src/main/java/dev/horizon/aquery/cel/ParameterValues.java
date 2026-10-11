package dev.horizon.aquery.cel;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The values of the parameters of an expression, which it reads as {@code params.name}: their types, and the values
 * in their canonical form.
 *
 * <p>
 * These Java types are values:
 *
 * <table>
 * <caption>Java types and expression types</caption>
 * <tr>
 * <th>Java</th>
 * <th>Type</th>
 * </tr>
 * <tr>
 * <td>{@code Boolean}</td>
 * <td>{@link Type.Scalar#BOOL}</td>
 * </tr>
 * <tr>
 * <td>{@code Long}, {@code Integer}, {@code Short}, {@code Byte}</td>
 * <td>{@link Type.Scalar#INT}</td>
 * </tr>
 * <tr>
 * <td>{@code Double}, {@code Float}</td>
 * <td>{@link Type.Scalar#DOUBLE}</td>
 * </tr>
 * <tr>
 * <td>{@code String}, {@code UUID}, an {@code Enum}</td>
 * <td>{@link Type.Scalar#STRING}</td>
 * </tr>
 * <tr>
 * <td>{@code byte[]}</td>
 * <td>{@link Type.Scalar#BYTES}</td>
 * </tr>
 * <tr>
 * <td>{@code Instant}, {@code OffsetDateTime}, {@code ZonedDateTime}</td>
 * <td>{@link Type.Scalar#TIMESTAMP}</td>
 * </tr>
 * <tr>
 * <td>{@code Duration}</td>
 * <td>{@link Type.Scalar#DURATION}</td>
 * </tr>
 * <tr>
 * <td>a {@code List} of one of these</td>
 * <td>{@link Type.ListOf}</td>
 * </tr>
 * </table>
 *
 * <p>
 * An enum is its name. A list without elements is a list of {@link Type#ANY}. The canonical form of a value is a
 * {@code Boolean}, {@code Long}, {@code Double}, {@code String}, {@code byte[]}, {@code Instant}, {@code Duration}, or
 * a {@code List} of these.
 */
public final class ParameterValues {

  private final Map<String, Type> types = new LinkedHashMap<>();
  private final Map<String, Object> canonical = new LinkedHashMap<>();

  /**
   * Reads the values of parameters.
   *
   * @param values the values by name
   * @throws IllegalArgumentException if a value is null or has a type that is not a value
   */
  public ParameterValues(Map<String, ?> values) {
    values.forEach((name, value) -> {
      try {
        types.put(name, typeOf(value));
      } catch (IllegalArgumentException wrong) {
        throw new IllegalArgumentException("the parameter " + name + ": " + wrong.getMessage(), wrong);
      }
      canonical.put(name, canonicalOf(value));
    });
  }

  /**
   * Gives the types of the parameters.
   *
   * @return the types by name, in the sequence of the values
   */
  public Map<String, Type> types() {
    return Collections.unmodifiableMap(types);
  }

  /**
   * Gives the values of the parameters in their canonical form.
   *
   * @return the values by name, in the sequence of the values
   */
  public Map<String, Object> canonical() {
    return Collections.unmodifiableMap(canonical);
  }

  private Type typeOf(Object value) {
    return switch (value) {
      case null -> throw new IllegalArgumentException("a value cannot be null; test a field with has()");
      case Boolean ignored -> Type.Scalar.BOOL;
      case Long ignored -> Type.Scalar.INT;
      case Integer ignored -> Type.Scalar.INT;
      case Short ignored -> Type.Scalar.INT;
      case Byte ignored -> Type.Scalar.INT;
      case Double ignored -> Type.Scalar.DOUBLE;
      case Float ignored -> Type.Scalar.DOUBLE;
      case String ignored -> Type.Scalar.STRING;
      case UUID ignored -> Type.Scalar.STRING;
      case Enum<?> ignored -> Type.Scalar.STRING;
      case byte[] ignored -> Type.Scalar.BYTES;
      case Instant ignored -> Type.Scalar.TIMESTAMP;
      case OffsetDateTime ignored -> Type.Scalar.TIMESTAMP;
      case ZonedDateTime ignored -> Type.Scalar.TIMESTAMP;
      case Duration ignored -> Type.Scalar.DURATION;
      case List<?> list -> new Type.ListOf(elementType(list));
      default -> throw new IllegalArgumentException("a value of " + value.getClass().getName() + " is not supported");
    };
  }

  private Type elementType(List<?> list) {
    Type element = Type.ANY;
    for (Object value : list) {
      Type type = typeOf(value);
      if (element == Type.ANY) {
        element = type;
      } else if (!element.equals(type)) {
        throw new IllegalArgumentException("a list has elements of one type, found " + element + " and " + type);
      }
    }
    return element;
  }

  private Object canonicalOf(Object value) {
    return switch (value) {
      case Integer number -> number.longValue();
      case Short number -> number.longValue();
      case Byte number -> number.longValue();
      case Float number -> number.doubleValue();
      case UUID uuid -> uuid.toString();
      case Enum<?> constant -> constant.name();
      case OffsetDateTime time -> time.toInstant();
      case ZonedDateTime time -> time.toInstant();
      case List<?> list -> {
        List<Object> elements = new ArrayList<>(list.size());
        for (Object element : list) {
          elements.add(canonicalOf(element));
        }
        yield elements;
      }
      default -> value;
    };
  }
}
