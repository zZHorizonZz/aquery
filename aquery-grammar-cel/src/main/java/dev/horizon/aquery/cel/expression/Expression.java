package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Scope;
import dev.horizon.aquery.cel.Type;

import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

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
 * <li>{@link FieldReference}: a field of a {@link Row}, as in {@code total_cents} or {@code new.total_cents}.
 * <li>{@link Variable}: the element of a list in {@link Quantifier}, as the {@code i} of
 * {@code items.exists(i, ...)}.
 * <li>{@link MemberAccess}: a member of a struct, as in {@code address.country}.
 * <li>{@link IndexAccess}: the value of a key of a map, as in {@code labels['tier']}.
 * <li>{@link Presence}: if a field or a member has a value, as in {@code has(address.city)}. A comparison with
 * {@code null}, as {@code expire_time != null}, is a presence too.
 * <li>{@link Not}, {@link Negative}, {@link BinaryOperation}: the operators.
 * <li>{@link Conditional}: one of two values, by a condition, as in {@code paid ? 'yes' : 'no'}.
 * <li>{@link Membership}: if a value is an element of a list or a key of a map.
 * <li>{@link ListLiteral}: a list of expressions, only after {@code in}.
 * <li>{@link FunctionCall}: a {@link Function} of the caller, for example {@code now()}.
 * <li>{@link StringMatch}: {@code startsWith}, {@code endsWith} or {@code contains} on a string.
 * <li>{@link Quantifier}: {@code exists} or {@code all} over the elements of a list.
 * <li>{@link Related}: an expression over the related rows of an edge, as in {@code customer.name} or
 * {@code items.exists(i, i.quantity > 1)}. A {@link FieldReference} inside it reads a field of a
 * {@link Row.Related} row.
 * </ul>
 */
public sealed interface Expression permits Expression.Literal, Expression.Parameter, Expression.FieldReference,
    Expression.Variable, Expression.MemberAccess, Expression.IndexAccess, Expression.Presence, Expression.Not,
    Expression.Negative, Expression.BinaryOperation, Expression.Conditional, Expression.Membership,
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
   * Tells if the value is stored as JSON: a field stored as JSON, or a struct, a list or a map inside one.
   *
   * @return true if it is
   */
  default boolean json() {
    return false;
  }

  /**
   * Tells if the value can be unset, so that a {@link Presence} can test it: a field that can be NULL, a member that
   * is optional, or a key of a map.
   *
   * @return true if it can
   */
  default boolean optional() {
    return false;
  }

  /**
   * Tells if the value is the same for every row: a constant, or a parameter that is not a list.
   *
   * @return true if it is
   */
  default boolean constant() {
    return false;
  }

  /**
   * Applies a step to the value of this node. The value of a related row stays inside its {@link Related.Value}, so
   * that {@code customer.address.country} reads the country in the related row.
   *
   * @param step the step
   * @return the node that the step gives
   */
  default Expression inside(UnaryOperator<Expression> step) {
    return step.apply(this);
  }

  /**
   * Names the node in an error message.
   *
   * @return the name, for example {@code the field 'state'}
   */
  default String describe() {
    return "the value";
  }

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

    @Override
    public boolean constant() {
      return true;
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

    @Override
    public boolean constant() {
      return type instanceof Type.Scalar;
    }
  }

  /**
   * A field of a row.
   *
   * @param field the field of the scope of the row
   * @param row the row
   * @param position the offset in the text
   */
  record FieldReference(Scope.Field field, Row row, int position) implements Expression {

    public FieldReference {
      Objects.requireNonNull(field, "field");
      Objects.requireNonNull(row, "row");
    }

    /**
     * Makes a field of the row of the expression.
     *
     * @param field the field of the scope
     * @param position the offset in the text
     */
    public FieldReference(Scope.Field field, int position) {
      this(field, Row.CURRENT, position);
    }

    @Override
    public Type type() {
      return field.type();
    }

    @Override
    public boolean json() {
      return field.json();
    }

    @Override
    public boolean optional() {
      return field.nullable();
    }

    @Override
    public String describe() {
      return "the field '" + field.name() + "'";
    }
  }

  /**
   * The row whose field a {@link FieldReference} reads. It is not a node.
   *
   * <ul>
   * <li>{@link Current}: the row of the expression.
   * <li>{@link Related}: a row of another table, reached along an edge. The {@link Expression.Related} node that
   * ranges over it names it.
   * <li>{@link Named}: a row that the caller names in {@link CelEnvironment}, such as the {@code new} row of a trigger.
   * </ul>
   */
  sealed interface Row permits Row.Current, Row.Related, Row.Named {

    /** The row of the expression. */
    Row CURRENT = new Current();

    /** The row of the expression. */
    record Current() implements Row {
    }

    /**
     * A row of another table, reached along an edge. Two related rows are the same row only if they are the same
     * object.
     *
     * @param name the name of the row in the text: the variable of {@code exists} or {@code all}, or the path of
     * edges, as in {@code customer}
     * @param edge the edge
     * @param from the row that the edge starts from
     */
    record Related(String name, Scope.Edge edge, Row from) implements Row {

      public Related {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(edge, "edge");
        Objects.requireNonNull(from, "from");
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
     * A row that the caller names in {@link CelEnvironment}.
     *
     * @param name the name of the row, for example {@code new}
     */
    record Named(String name) implements Row {

      public Named {
        Objects.requireNonNull(name, "name");
      }
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

    @Override
    public boolean json() {
      return !(type instanceof Type.Scalar);
    }
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

    @Override
    public boolean json() {
      return !(type() instanceof Type.Scalar);
    }

    @Override
    public boolean optional() {
      return member.optional();
    }

    @Override
    public String describe() {
      return "the member '" + member.name() + "'";
    }
  }

  /**
   * The value of a key of a map, or of an index of a list.
   *
   * @param operand the map or the list
   * @param key the key or the index
   * @param type the type of the value
   * @param position the offset in the text
   */
  record IndexAccess(Expression operand, Expression key, Type type, int position) implements Expression {

    @Override
    public boolean json() {
      return !(type instanceof Type.Scalar);
    }

    @Override
    public boolean optional() {
      return operand.type() instanceof Type.MapOf;
    }
  }

  /**
   * Tells if a field, a member or a key has a value.
   *
   * @param operand a node that is {@linkplain Expression#optional() optional}
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
  record BinaryOperation(Operator operator, Expression left, Expression right, Type type, int position)
      implements
        Expression {
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
   * One of two values, by a condition: {@code condition ? then : otherwise}. Both values have the same type.
   *
   * @param condition the condition
   * @param then the value if the condition is true
   * @param otherwise the value if the condition is false
   * @param type the type of the value
   * @param position the offset in the text
   */
  record Conditional(Expression condition, Expression then, Expression otherwise, Type type, int position)
      implements
        Expression {

    public Conditional {
      Objects.requireNonNull(condition, "condition");
      Objects.requireNonNull(then, "then");
      Objects.requireNonNull(otherwise, "otherwise");
      Objects.requireNonNull(type, "type");
    }
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
   * A call of a {@link Function} of the caller, for example {@code now()}.
   *
   * @param function the function
   * @param arguments the arguments: the value that a member function is called on, then the arguments of the call
   * @param position the offset in the text
   */
  record FunctionCall(Function function, List<Expression> arguments, int position) implements Expression {

    public FunctionCall {
      Objects.requireNonNull(function, "function");
      arguments = List.copyOf(arguments);
    }

    @Override
    public Type type() {
      return function.overload().result();
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
  record Quantifier(Kind kind, Variable variable, Expression range, Expression predicate, int position)
      implements
        Expression {

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
   * An expression over the related rows of an edge. Its body reads the fields of its {@link Row.Related row}.
   *
   * <ul>
   * <li>{@link Value}: the value of the body for the one related row of an edge to one row.
   * <li>{@link Exists}: true if one or more related rows match the body.
   * <li>{@link All}: true if all related rows match the body.
   * </ul>
   */
  sealed interface Related extends Expression permits Related.Value, Related.Exists, Related.All {

    /**
     * Gives the related row, which the fields of the body read.
     *
     * @return the row
     */
    Row.Related row();

    /**
     * Gives the value or the condition for the related row.
     *
     * @return the body
     */
    Expression body();

    /**
     * The value of the body for the one related row of an edge to one row, as the {@code customer.name} of
     * {@code customer.name == 'Ana'}. If there is no related row, the value is the default value of its type, as for
     * a field that is not set.
     *
     * @param row the related row
     * @param body the value
     * @param position the offset in the text
     */
    record Value(Row.Related row, Expression body, int position) implements Related {

      public Value {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(body, "body");
      }

      @Override
      public Type type() {
        return body.type();
      }

      @Override
      public Expression inside(UnaryOperator<Expression> step) {
        return new Value(row, body.inside(step), position);
      }
    }

    /**
     * True if one or more related rows match the body, as in {@code items.exists(i, i.quantity > 1)}.
     * {@code has(customer)} is an exists with the body {@code true}.
     *
     * @param row the related row
     * @param body the condition
     * @param position the offset in the text
     */
    record Exists(Row.Related row, Expression body, int position) implements Related {

      public Exists {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(body, "body");
      }

      @Override
      public Type type() {
        return Type.Scalar.BOOL;
      }
    }

    /**
     * True if all related rows match the body, as in {@code items.all(i, i.quantity > 1)}.
     *
     * @param row the related row
     * @param body the condition
     * @param position the offset in the text
     */
    record All(Row.Related row, Expression body, int position) implements Related {

      public All {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(body, "body");
      }

      @Override
      public Type type() {
        return Type.Scalar.BOOL;
      }
    }
  }
}
