package dev.horizon.aquery.cel;

import java.util.List;
import java.util.Objects;

/**
 * A node of a compiled expression.
 *
 * <p>
 * The compiler checks the text of an expression and gives this tree. Each node has its type and its offset in the
 * text. The tree does not depend on the language of the text or on a database.
 *
 * <p>
 * The tree has these nodes:
 *
 * <ul>
 * <li>{@link Literal}: a constant, for example {@code 'OPEN'} or {@code duration('30s')}.
 * <li>{@link Parameter}: a value that the caller gives with the expression, as in {@code params.min}.
 * <li>{@link FieldReference}: a field of the row, as in {@code total_cents}.
 * <li>{@link Variable}: the element of a list in {@link Quantifier}, as the {@code i} of
 * {@code items.exists(i, ...)}.
 * <li>{@link MemberAccess}: a member of a struct, as in {@code address.country}.
 * <li>{@link IndexAccess}: the value of a key of a map, as in {@code labels['tier']}.
 * <li>{@link Presence}: if a field or a member has a value, as in {@code has(address.city)}.
 * <li>{@link Not}, {@link Negative}, {@link BinaryOperation}: the operators.
 * <li>{@link Membership}: if a value is an element of a list or a key of a map.
 * <li>{@link ListLiteral}: a list of expressions, only after {@code in}.
 * <li>{@link FunctionCall}: a function that the caller declared, for example {@code now()}.
 * <li>{@link StringMatch}: {@code startsWith}, {@code endsWith} or {@code contains} on a string.
 * <li>{@link Quantifier}: {@code exists} or {@code all} over the elements of a list.
 * <li>{@link Related}: an expression over the related rows of an edge, as in {@code customer.name} or
 * {@code items.exists(i, i.quantity > 1)}. A {@link FieldReference} inside it reads a field of a related {@link Row}.
 * </ul>
 */
public sealed interface Expression permits Expression.Literal, Expression.Parameter, Expression.FieldReference,
    Expression.Variable, Expression.MemberAccess, Expression.IndexAccess,
    Expression.Presence, Expression.Not, Expression.Negative, Expression.BinaryOperation, Expression.Membership,
    Expression.ListLiteral, Expression.FunctionCall, Expression.StringMatch, Expression.Quantifier,
    Expression.Related {

  /**
   * Gives the type of the value of the node.
   *
   * @return the type
   */
  Type type();

  /**
   * Gives the offset of the node in the text of the expression.
   *
   * @return the offset in characters, or -1 if it is not known
   */
  int position();

  /**
   * A constant.
   *
   * @param type the type of the constant
   * @param value the value: a {@code Boolean}, {@code Long}, {@code Double}, {@code String}, {@code byte[]},
   * {@code java.time.Instant} or {@code java.time.Duration}
   * @param position the offset in the text
   */
  record Literal(Type type, Object value, int position) implements Expression {

    public Literal {
      Objects.requireNonNull(type, "type");
      Objects.requireNonNull(value, "value");
    }
  }

  /**
   * A value that the caller gives with the expression.
   *
   * @param name the name of the value, the {@code min} of {@code params.min}
   * @param type the type of the value
   * @param position the offset in the text
   */
  record Parameter(String name, Type type, int position) implements Expression {
  }

  /**
   * A field of the row, or of a related row.
   *
   * @param field the field of the scope of the row
   * @param row the related row, or null for the row of the expression
   * @param position the offset in the text
   */
  record FieldReference(Scope.Field field, Row row, int position) implements Expression {

    /**
     * Makes a field of the row of the expression.
     *
     * @param field the field of the scope
     * @param position the offset in the text
     */
    public FieldReference(Scope.Field field, int position) {
      this(field, null, position);
    }

    @Override
    public Type type() {
      return field.type();
    }
  }

  /**
   * A row of another table, reached along an edge. It is not a node: the {@link Related} node that ranges over it
   * names it, and each {@link FieldReference} of its fields.
   *
   * <p>
   * Two rows are the same row only if they are the same object.
   *
   * @param name the name of the row in the text: the variable of {@code exists} or {@code all}, or the path of edges,
   * as in {@code customer}
   * @param edge the edge
   * @param from the row that the edge starts from, or null for the row of the expression
   */
  record Row(String name, Scope.Edge edge, Row from) {

    public Row {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(edge, "edge");
    }

    @Override
    public boolean equals(Object other) {
      return this == other;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  /**
   * The element of a list, inside a {@link Quantifier}.
   *
   * @param name the name that the quantifier gives to the element
   * @param type the type of the element
   * @param position the offset in the text
   */
  record Variable(String name, Type type, int position) implements Expression {
  }

  /**
   * A member of a struct.
   *
   * @param operand the struct
   * @param member the member
   * @param position the offset in the text
   */
  record MemberAccess(Expression operand, Type.Member member, int position) implements Expression {

    @Override
    public Type type() {
      return member.type();
    }
  }

  /**
   * The value of a key of a map.
   *
   * @param operand the map
   * @param key the key
   * @param type the type of the value
   * @param position the offset in the text
   */
  record IndexAccess(Expression operand, Expression key, Type type, int position) implements Expression {
  }

  /**
   * Tells if a field, a member or a key has a value.
   *
   * @param operand a {@link FieldReference} that is nullable, a {@link MemberAccess} that is optional, or an {@link IndexAccess}
   * @param position the offset in the text
   */
  record Presence(Expression operand, int position) implements Expression {

    @Override
    public Type type() {
      return Type.Scalar.BOOL;
    }
  }

  /**
   * The negation of a condition: {@code !a}.
   *
   * @param operand the condition
   * @param position the offset in the text
   */
  record Not(Expression operand, int position) implements Expression {

    @Override
    public Type type() {
      return Type.Scalar.BOOL;
    }
  }

  /**
   * The negative of a number or a duration: {@code -a}.
   *
   * @param operand the number or the duration
   * @param position the offset in the text
   */
  record Negative(Expression operand, int position) implements Expression {

    @Override
    public Type type() {
      return operand.type();
    }
  }

  /**
   * An operator with two operands.
   *
   * @param operator the operator
   * @param left the left operand
   * @param right the right operand
   * @param type the type of the result
   * @param position the offset in the text
   */
  record BinaryOperation(Operator operator, Expression left, Expression right, Type type, int position) implements Expression {
  }

  /**
   * The operators of {@link BinaryOperation}.
   *
   * <p>
   * {@link #ADD} adds numbers and durations, adds a duration to a timestamp, and joins strings. {@link #SUBTRACT}
   * also gives the duration between two timestamps.
   */
  enum Operator {
    AND,
    OR,
    EQUALS,
    NOT_EQUALS,
    LESS,
    LESS_EQUALS,
    GREATER,
    GREATER_EQUALS,
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    MODULO
  }

  /**
   * Tells if a value is an element of a list, or a key of a map.
   *
   * @param element the value
   * @param collection a {@link ListLiteral}, a list {@link Parameter}, or a list or map in JSON
   * @param position the offset in the text
   */
  record Membership(Expression element, Expression collection, int position) implements Expression {

    @Override
    public Type type() {
      return Type.Scalar.BOOL;
    }
  }

  /**
   * A list of expressions, as in {@code ['gold', 'platinum']}.
   *
   * @param elements the elements
   * @param type the type of the list
   * @param position the offset in the text
   */
  record ListLiteral(List<Expression> elements, Type type, int position) implements Expression {

    public ListLiteral {
      elements = List.copyOf(elements);
    }
  }

  /**
   * A call of a function that the caller of the compiler declared, for example {@code now()}.
   *
   * @param function the function
   * @param arguments the arguments, in the order of the parameters of the function
   * @param position the offset in the text
   */
  record FunctionCall(Function function, List<Expression> arguments, int position) implements Expression {

    public FunctionCall {
      Objects.requireNonNull(function, "function");
      arguments = List.copyOf(arguments);
    }

    @Override
    public Type type() {
      return function.result();
    }
  }

  /**
   * A test of a string against a part of it: {@code s.startsWith(prefix)}, {@code s.endsWith(suffix)} or
   * {@code s.contains(part)}.
   *
   * @param kind which of the three
   * @param string the string
   * @param part the part
   * @param position the offset in the text
   */
  record StringMatch(Kind kind, Expression string, Expression part, int position) implements Expression {

    @Override
    public Type type() {
      return Type.Scalar.BOOL;
    }

    /** Where the part must be: at the start, at the end, or anywhere. */
    public enum Kind {
      STARTS_WITH,
      ENDS_WITH,
      CONTAINS
    }
  }

  /**
   * {@code exists} or {@code all} over the elements of a list.
   *
   * @param kind which of the two
   * @param variable the element, as the predicate names it
   * @param range the list
   * @param predicate the condition for each element
   * @param position the offset in the text
   */
  record Quantifier(Kind kind, Variable variable, Expression range, Expression predicate, int position) implements Expression {

    @Override
    public Type type() {
      return Type.Scalar.BOOL;
    }

    /** {@link #EXISTS}: one or more elements match. {@link #ALL}: all elements match. */
    public enum Kind {
      EXISTS,
      ALL
    }
  }

  /**
   * An expression over the related rows of an edge.
   *
   * <ul>
   * <li>{@link Kind#VALUE}: the value of the body for the one related row of an edge to one row, as the
   * {@code customer.name} of {@code customer.name == 'Ana'}. If there is no related row, the value is the default
   * value of its type, as for a field that is not set.
   * <li>{@link Kind#EXISTS}: true if one or more related rows match the body, as in
   * {@code items.exists(i, i.quantity > 1)}. {@code has(customer)} is EXISTS with the body {@code true}.
   * <li>{@link Kind#ALL}: true if all related rows match the body, as in {@code items.all(i, i.quantity > 1)}.
   * </ul>
   *
   * @param kind which of the three
   * @param row the related row, which the fields of the body can read
   * @param body the value or the condition for the related row
   * @param position the offset in the text
   */
  record Related(Kind kind, Row row, Expression body, int position) implements Expression {

    public Related {
      Objects.requireNonNull(kind, "kind");
      Objects.requireNonNull(row, "row");
      Objects.requireNonNull(body, "body");
    }

    @Override
    public Type type() {
      return kind == Kind.VALUE ? body.type() : Type.Scalar.BOOL;
    }

    /** {@link #VALUE}, {@link #EXISTS} or {@link #ALL}. */
    public enum Kind {
      VALUE,
      EXISTS,
      ALL
    }
  }
}
