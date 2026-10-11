package dev.horizon.aquery.cel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The type of a value in an expression, as CEL sees it.
 *
 * <p>
 * A field, a parameter and each part of an expression have a type. The compiler checks the types before it writes
 * any SQL. Thus an expression that compares a string with a number fails when it is compiled, not in the database.
 *
 * <p>
 * The types are the types of CEL: {@link Scalar} for single values, {@link ListOf} and {@link MapOf} for
 * collections, and {@link Struct} for a message with named members. {@link #ANY} is the element type of an empty
 * list, whose elements can have any type.
 */
public sealed interface Type permits Type.Scalar, Type.ListOf, Type.MapOf, Type.Struct, Type.Any {

  /** The element type of a list without elements. */
  Type ANY = new Any();

  /**
   * A single value.
   *
   * <p>
   * A timestamp is an instant, with microseconds. A duration is a length of time, with microseconds. An enum value
   * is a {@link #STRING}, its name.
   */
  enum Scalar implements Type {
    BOOL,
    INT,
    DOUBLE,
    STRING,
    BYTES,
    TIMESTAMP,
    DURATION
  }

  /**
   * A list of values of one type.
   *
   * @param element the type of each element
   */
  record ListOf(Type element) implements Type {

    public ListOf {
      Objects.requireNonNull(element, "element");
    }
  }

  /**
   * A map from keys to values.
   *
   * @param key the type of each key: {@link Scalar#STRING}, {@link Scalar#INT} or {@link Scalar#BOOL}
   * @param value the type of each value
   */
  record MapOf(Type key, Type value) implements Type {

    public MapOf {
      if (key != Scalar.STRING && key != Scalar.INT && key != Scalar.BOOL) {
        throw new IllegalArgumentException("a map key is a string, an integer or a boolean, was " + key);
      }
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * A message with named members, for example an address with a country and a city.
   *
   * @param name the full name of the message, for example {@code shop.v1.Address}. Two structs with the same name
   * must have the same members.
   * @param members the members, in the sequence of their declaration
   */
  record Struct(String name, List<Member> members) implements Type {

    public Struct {
      Objects.requireNonNull(name, "name");
      members = List.copyOf(members);
      Map<String, Member> byName = new LinkedHashMap<>();
      for (Member member : members) {
        if (byName.putIfAbsent(member.name(), member) != null) {
          throw new IllegalArgumentException("the struct " + name + " has two members named " + member.name());
        }
      }
    }

    /**
     * Finds a member by its name.
     *
     * @param name the name of the member
     * @return the member, or empty if the struct has no member with the name
     */
    public Optional<Member> member(String name) {
      return members.stream().filter(member -> member.name().equals(name)).findFirst();
    }
  }

  /**
   * A member of a {@link Struct}.
   *
   * @param name the name of the member, which is also its key in JSON
   * @param type the type of the member
   * @param optional true if the member can be absent. Only such a member can be tested with {@code has()}; a member
   * that is not optional always has a value, its default when it was not set.
   */
  record Member(String name, Type type, boolean optional) {

    public Member {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(type, "type");
    }
  }

  /** Any type. Only the elements of an empty list have it. */
  record Any() implements Type {
  }
}
