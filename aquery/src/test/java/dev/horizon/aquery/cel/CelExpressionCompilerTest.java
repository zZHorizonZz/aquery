package dev.horizon.aquery.cel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CelExpressionCompilerTest {
  private static final CelExpressionCompiler COMPILER = new CelExpressionCompiler();

  private static final Type.Struct ADDRESS = new Type.Struct("shop.Address", List.of(
      new Type.Member("country", Type.Scalar.STRING, false),
      new Type.Member("city", Type.Scalar.STRING, true)));

  private static final Type.Struct ITEM = new Type.Struct("shop.Item", List.of(
      new Type.Member("sku", Type.Scalar.STRING, false),
      new Type.Member("quantity", Type.Scalar.INT, false)));

  private static final Scope ORDER = Scope.builder("shop.Order")
      .uuid("id", "id", false)
      .column("state", Type.Scalar.STRING, "state")
      .column("total_cents", Type.Scalar.INT, "total_cents")
      .column("rate", Type.Scalar.DOUBLE, "rate")
      .column("paid", Type.Scalar.BOOL, "paid")
      .column("create_time", Type.Scalar.TIMESTAMP, "create_time")
      .nullable("expire_time", Type.Scalar.TIMESTAMP, "expire_time")
      .column("timeout", Type.Scalar.DURATION, "timeout")
      .json("address", ADDRESS, "address", true)
      .json("tags", new Type.ListOf(Type.Scalar.STRING), "tags", false)
      .json("items", new Type.ListOf(ITEM), "items", false)
      .json("labels", new Type.MapOf(Type.Scalar.STRING, Type.Scalar.STRING), "labels", false)
      .build();

  private static final Scope REGION = Scope.builder("shop.Region")
      .column("id", Type.Scalar.STRING, "id")
      .column("name", Type.Scalar.STRING, "name")
      .build();

  private static final Scope CUSTOMER = Scope.builder("shop.Customer")
      .column("id", Type.Scalar.STRING, "id")
      .column("name", Type.Scalar.STRING, "name")
      .column("region_id", Type.Scalar.STRING, "region_id")
      .nullable("vip_time", Type.Scalar.TIMESTAMP, "vip_time")
      .json("address", ADDRESS, "address", true)
      .edge("region", false, "region_id", "id", () -> REGION)
      .edge("orders", true, "id", "customer_id", () -> CelExpressionCompilerTest.SALE)
      .build();

  private static final Scope SALE = Scope.builder("shop.Sale")
      .column("id", Type.Scalar.STRING, "id")
      .column("customer_id", Type.Scalar.STRING, "customer_id")
      .column("total_cents", Type.Scalar.INT, "total_cents")
      .edge("customer", false, "customer_id", "id", () -> CUSTOMER)
      .edge("lines", true, "id", "sale_id", () -> CelExpressionCompilerTest.LINE)
      .build();

  private static final Scope LINE = Scope.builder("shop.Line")
      .column("id", Type.Scalar.STRING, "id")
      .column("sale_id", Type.Scalar.STRING, "sale_id")
      .column("sku", Type.Scalar.STRING, "sku")
      .column("quantity", Type.Scalar.INT, "quantity")
      .edge("sale", false, "sale_id", "id", () -> SALE)
      .build();

  private static final Map<String, Object> PARAMS = Map.of("min", 100L, "since", Instant.EPOCH, "name", "dan",
      "ids", List.of("a", "b"), "wait", Duration.ofSeconds(5));

  private static CompiledExpression compile(String text, Type result, Function... functions) {
    return COMPILER.compile(text, ORDER, new ParameterValues(PARAMS).types(), result, Set.of(functions));
  }

  @ParameterizedTest(name = "[{0}] is {1}")
  @CsvSource(delimiterString = " ~ ", quoteCharacter = '`', textBlock = """
      state == 'OPEN' ~ (== state 'OPEN')
      total_cents > params.min && paid ~ (&& (> total_cents $min) paid)
      !paid || rate >= 1.5 ~ (|| (! paid) (>= rate 1.5))
      100 <= total_cents ~ (<= 100 total_cents)
      state in ['OPEN', 'PAID'] ~ (in state ['OPEN' 'PAID'])
      id in params.ids ~ (in id $ids)
      'vip' in tags ~ (in 'vip' tags)
      'tier' in labels ~ (in 'tier' labels)
      has(expire_time) ~ (has expire_time)
      has(address.city) ~ (has address.city)
      has(labels.tier) ~ (has labels['tier'])
      address.country == 'CZ' ~ (== address.country 'CZ')
      labels['tier'] == 'gold' ~ (== labels['tier'] 'gold')
      tags[0] == params.name ~ (== tags[0] $name)
      state.startsWith('OP') ~ (STARTS_WITH state 'OP')
      state.endsWith(params.name) ~ (ENDS_WITH state $name)
      state.contains('P') ~ (CONTAINS state 'P')
      items.exists(i, i.sku == 'MUG-1' && i.quantity > 1) ~ (EXISTS i items (&& (== i.sku 'MUG-1') (> i.quantity 1)))
      items.all(i, i.quantity > total_cents) ~ (ALL i items (> i.quantity total_cents))
      create_time > timestamp('2024-01-01T00:00:00Z') ~ (> create_time 2024-01-01T00:00:00Z)
      create_time + duration('1h30m') < params.since ~ (< (+ create_time PT1H30M) $since)
      create_time - expire_time > params.wait ~ (> (- create_time expire_time) $wait)
      total_cents * 2 - -3 == 10 % 4 ~ (== (- (* total_cents 2) -3) (% 10 4))
      -total_cents < 0 ~ (< (- total_cents) 0)
      state + '!' == 'OPEN!' ~ (== (+ state '!') 'OPEN!')
      """)
  @DisplayName("gives the tree of a condition")
  void condition(String text, String tree) {
    assertThat(show(compile(text, Type.Scalar.BOOL).root())).isEqualTo(tree);
  }

  @ParameterizedTest(name = "[{0}] is {1}")
  @CsvSource(delimiterString = " ~ ", quoteCharacter = '`', textBlock = """
      customer.name == 'Ana' ~ (== (VALUE customer customer/name) 'Ana')
      customer.address.country == 'CZ' ~ (== (VALUE customer customer/address.country) 'CZ')
      customer.region.name == params.name ~ (== (VALUE customer (VALUE customer.region customer.region/name)) $name)
      has(customer) ~ (EXISTS customer true)
      has(customer.vip_time) ~ (VALUE customer (has customer/vip_time))
      has(customer.address.city) ~ (VALUE customer (has customer/address.city))
      has(customer.region) ~ (VALUE customer (EXISTS customer.region true))
      has(lines) ~ (EXISTS lines true)
      lines.exists(l, l.sku == 'MUG' && l.quantity > total_cents) ~ (EXISTS l (&& (== l/sku 'MUG') (> l/quantity total_cents)))
      lines.all(l, l.quantity > 0) ~ (ALL l (> l/quantity 0))
      customer.orders.exists(o, o.total_cents > 100) ~ (VALUE customer (EXISTS o (> o/total_cents 100)))
      lines.exists(l, l.sale.customer.name == 'Ana') ~ (EXISTS l (== (VALUE l.sale (VALUE l.sale.customer l.sale.customer/name)) 'Ana'))
      lines.exists(l, has(l.sale)) ~ (EXISTS l (EXISTS l.sale true))
      customer.name.startsWith('A') ~ (STARTS_WITH (VALUE customer customer/name) 'A')
      """)
  @DisplayName("gives the tree of a condition across edges")
  void edges(String text, String tree) {
    assertThat(show(COMPILER.compile(text, SALE, new ParameterValues(PARAMS).types(), Type.Scalar.BOOL, Set.of()).root()))
        .isEqualTo(tree);
  }

  @Test
  @DisplayName("names the row that each edge starts from")
  void edgeRows() {
    Expression.Related customer = (Expression.Related) COMPILER.compile("customer.orders.exists(o, o.customer.name == 'Ana')",
        SALE, Map.of(), Type.Scalar.BOOL, Set.of()).root();
    assertThat(customer.row().from()).isNull();
    assertThat(customer.row().edge().name()).isEqualTo("customer");
    Expression.Related orders = (Expression.Related) customer.body();
    assertThat(orders.row().from()).isSameAs(customer.row());
    Expression.BinaryOperation equals = (Expression.BinaryOperation) orders.body();
    Expression.Related back = (Expression.Related) equals.left();
    assertThat(back.row().from()).isSameAs(orders.row());
    assertThat(((Expression.FieldReference) back.body()).row()).isSameAs(back.row());
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      has(customer.name)                     | always has a value
      lines.exists(l, l.quantity > 0) && customer.orders.all(o, has(o.customer.id)) | always has a value
      customer.exists(c, true)               | invalid expression
      """)
  @DisplayName("refuses edges used as values")
  void refusesEdges(String text, String message) {
    assertThatThrownBy(() -> COMPILER.compile(text, SALE, new ParameterValues(PARAMS).types(), Type.Scalar.BOOL, Set.of()))
        .isInstanceOf(InvalidExpressionException.class).hasMessageContaining(message);
  }

  @ParameterizedTest(name = "[{0}] is a {1}")
  @CsvSource(delimiter = '|', textBlock = """
      total_cents - params.min        | INT
      rate * 2.0                      | DOUBLE
      state + params.name             | STRING
      create_time + params.wait       | TIMESTAMP
      create_time - create_time       | DURATION
      timeout + duration('1s')        | DURATION
      paid && total_cents > 0         | BOOL
      address.country                 | STRING
      """)
  @DisplayName("gives the type of a value")
  void value(String text, Type.Scalar type) {
    assertThat(compile(text, type).type()).isEqualTo(type);
  }

  private record Declared(String name, List<Type> parameters, Type result) implements Function {
  }

  @Test
  @DisplayName("calls the functions that the caller declares, and only those")
  void functions() {
    Function now = new Declared("now", List.of(), Type.Scalar.TIMESTAMP);
    Function round = new Declared("round_to", List.of(Type.Scalar.DOUBLE, Type.Scalar.INT), Type.Scalar.DOUBLE);
    assertThat(show(compile("expire_time > now()", Type.Scalar.BOOL, now).root()))
        .isEqualTo("(> expire_time (now))");
    Expression.FunctionCall call = (Expression.FunctionCall) compile("round_to(rate, 2)", Type.Scalar.DOUBLE, round)
        .root();
    assertThat(call.function()).isSameAs(round);
    assertThat(show(call)).isEqualTo("(round_to rate 2)");
    assertThatThrownBy(() -> compile("expire_time > now()", Type.Scalar.BOOL))
        .isInstanceOf(InvalidExpressionException.class)
        .hasMessageContaining("undeclared reference to 'now'");
    assertThatThrownBy(() -> compile("round_to(state, 2) > 1.0", Type.Scalar.BOOL, round))
        .isInstanceOf(InvalidExpressionException.class)
        .hasMessageContaining("no matching overload");
  }

  @Test
  @DisplayName("keeps the types of the parameters")
  void params() {
    CompiledExpression expression = compile("total_cents > params.min", Type.Scalar.BOOL);
    assertThat(expression.parameters()).containsEntry("min", Type.Scalar.INT).containsEntry("ids",
        new Type.ListOf(Type.Scalar.STRING));
  }

  @ParameterizedTest(name = "[{0}]")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      state == 1                                 | no matching overload
      unknown == 1                               | undeclared reference to 'unknown'
      params.missing == 1                        | undefined field 'missing'
      state.matches('O.*')                       | matches() is not supported
      size(tags) > 1                             | size() is not supported
      int(rate) > 1                              | int() is not supported
      paid ? true : false                        | the conditional operator
      expire_time == null                        | null is not supported
      total_cents == 1u                          | applied to '(int, uint)'
      total_cents / params.min > 1               | must be a constant that is not zero
      total_cents % 0 == 1                       | must be a constant that is not zero
      has(state)                                 | has() tests only a field that can be unset
      has(address.country)                       | has() tests only a member that can be unset
      tags.exists_one(t, t == 'a')               | undeclared reference to 'exists_one'
      tags.map(t, t) == ['a']                    | undeclared reference to 'map'
      ['a', 'b'].exists(t, t == state)           | supported only over a list field
      tags[total_cents] == 'a'                   | must be a constant or a parameter
      state in labels                            | a key 'in' a map must be a constant or a parameter
      {'a': 1}['a'] == 1                         | a map literal is not supported
      create_time > timestamp('yesterday')       | timestamp
      create_time > timestamp(state)             | expected a string constant in timestamp()
      """)
  @DisplayName("refuses what SQL cannot answer, with a message that tells what")
  void refuses(String text, String message) {
    assertThatThrownBy(() -> compile(text, Type.Scalar.BOOL))
        .isInstanceOf(InvalidExpressionException.class)
        .hasMessageContaining(message)
        .extracting(error -> ((InvalidQueryException) error).field())
        .isEqualTo(InvalidQueryException.EXPRESSION);
  }

  @Test
  @DisplayName("refuses an expression of another type than the caller expects")
  void resultType() {
    assertThatThrownBy(() -> compile("total_cents + 1", Type.Scalar.BOOL))
        .isInstanceOf(InvalidExpressionException.class)
        .hasMessageContaining("expected an expression of type bool but it is of type int");
  }

  @ParameterizedTest(name = "[{0}] at [{1}]")
  @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
      paid && unknown          | u
      paid && state.matches('x') | (
      total_cents / params.min > 1 | .
      """)
  @DisplayName("says where in the text the problem is")
  void positions(String text, String character) {
    assertThatThrownBy(() -> compile(text, Type.Scalar.BOOL))
        .isInstanceOfSatisfying(InvalidExpressionException.class,
            error -> assertThat(text.substring(error.position(), error.position() + 1)).isEqualTo(character));
  }

  private static String show(Expression expression) {
    return switch (expression) {
      case Expression.Literal literal when literal.value() instanceof String text -> "'" + text + "'";
      case Expression.Literal literal -> String.valueOf(literal.value());
      case Expression.Parameter param -> "$" + param.name();
      case Expression.FieldReference field -> (field.row() == null ? "" : field.row().name() + "/") + field.field().name();
      case Expression.Variable variable -> variable.name();
      case Expression.MemberAccess member -> show(member.operand()) + "." + member.member().name();
      case Expression.IndexAccess index -> show(index.operand()) + "[" + show(index.key()) + "]";
      case Expression.Presence has -> "(has " + show(has.operand()) + ")";
      case Expression.Not not -> "(! " + show(not.operand()) + ")";
      case Expression.Negative negate -> "(- " + show(negate.operand()) + ")";
      case Expression.BinaryOperation binary ->
        "(" + symbol(binary.operator()) + " " + show(binary.left()) + " " + show(binary.right())
            + ")";
      case Expression.Membership in -> "(in " + show(in.element()) + " " + show(in.collection()) + ")";
      case Expression.ListLiteral list -> list.elements().stream().map(CelExpressionCompilerTest::show)
          .collect(Collectors.joining(" ", "[", "]"));
      case Expression.StringMatch match -> "(" + match.kind() + " " + show(match.string()) + " " + show(match.part())
          + ")";
      case Expression.FunctionCall call -> "(" + call.function().name()
          + call.arguments().stream().map(argument -> " " + show(argument)).collect(Collectors.joining()) + ")";
      case Expression.Quantifier quantifier -> "(" + quantifier.kind() + " " + quantifier.variable().name() + " "
          + show(quantifier.range()) + " " + show(quantifier.predicate()) + ")";
      case Expression.Related related -> "(" + related.kind() + " " + related.row().name() + " " + show(related.body()) + ")";
    };
  }

  private static String symbol(Expression.Operator operator) {
    return switch (operator) {
      case AND -> "&&";
      case OR -> "||";
      case EQUALS -> "==";
      case NOT_EQUALS -> "!=";
      case LESS -> "<";
      case LESS_EQUALS -> "<=";
      case GREATER -> ">";
      case GREATER_EQUALS -> ">=";
      case ADD -> "+";
      case SUBTRACT -> "-";
      case MULTIPLY -> "*";
      case DIVIDE -> "/";
      case MODULO -> "%";
    };
  }
}
