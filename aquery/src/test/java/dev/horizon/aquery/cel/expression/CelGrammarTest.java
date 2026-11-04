package dev.horizon.aquery.cel.expression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.cel.Macro;
import dev.horizon.aquery.cel.MacroCall;
import dev.horizon.aquery.cel.Scope;
import dev.horizon.aquery.cel.Syntax;
import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.expression.Expression.Row;
import dev.horizon.aquery.cel.expression.operation.Conditional;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CelGrammarTest {

  private static final CelGrammar GRAMMAR = CelGrammar.standard();

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
      .nullable("note", Type.Scalar.STRING, "note")
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
      .edge("orders", true, "id", "customer_id", () -> CelGrammarTest.SALE)
      .build();

  private static final Scope SALE = Scope.builder("shop.Sale")
      .column("id", Type.Scalar.STRING, "id")
      .column("customer_id", Type.Scalar.STRING, "customer_id")
      .column("total_cents", Type.Scalar.INT, "total_cents")
      .edge("customer", false, "customer_id", "id", () -> CUSTOMER)
      .edge("lines", true, "id", "sale_id", () -> CelGrammarTest.LINE)
      .build();

  private static final Scope LINE = Scope.builder("shop.Line")
      .column("id", Type.Scalar.STRING, "id")
      .column("sale_id", Type.Scalar.STRING, "sale_id")
      .column("sku", Type.Scalar.STRING, "sku")
      .column("quantity", Type.Scalar.INT, "quantity")
      .edge("sale", false, "sale_id", "id", () -> SALE)
      .build();

  private static final Parameters PARAMS = new ParameterValues(Map.of("min", 100L, "since", Instant.EPOCH, "name",
      "dan", "ids", List.of("a", "b"), "wait", Duration.ofSeconds(5))).parameters();

  private static final CelEnvironment TRIGGER = CelEnvironment.of(ORDER).with("new", ORDER).with("old", ORDER);

  private record Declared(String name, Overload overload) implements Function {
  }

  private static CompiledExpression compile(String text, Type result) {
    return GRAMMAR.compile(text, CelEnvironment.of(ORDER).with(PARAMS), result);
  }

  private static CompiledExpression compile(CelGrammar grammar, String text, Type result) {
    return grammar.compile(text, CelEnvironment.of(ORDER).with(PARAMS), result);
  }

  private static String condition(String text) {
    return show(compile(text, Type.Scalar.BOOL).root());
  }

  @Nested
  @DisplayName("a condition on the fields of the row")
  class Conditions {

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
        total_cents / -2 > 1 ~ (> (/ total_cents -2) 1)
        -total_cents < 0 ~ (< (- total_cents) 0)
        state + '!' == 'OPEN!' ~ (== (+ state '!') 'OPEN!')
        paid ? total_cents > 0 : state == 'OPEN' ~ (? paid (> total_cents 0) (== state 'OPEN'))
        (paid ? total_cents : 0) > params.min ~ (> (? paid total_cents 0) $min)
        """)
    @DisplayName("gives the tree of the condition")
    void givesTheTree(String text, String tree) {
      assertThat(condition(text)).isEqualTo(tree);
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
        paid ? state : 'none'           | STRING
        """)
    @DisplayName("gives the type of the value")
    void givesTheType(String text, Type.Scalar type) {
      assertThat(compile(text, type).type()).isEqualTo(type);
    }

    @Test
    @DisplayName("keeps the types of the parameters")
    void keepsTheParameters() {
      assertThat(compile("total_cents > params.min", Type.Scalar.BOOL).parameters().types())
          .containsEntry("min", Type.Scalar.INT).containsEntry("ids", new Type.ListOf(Type.Scalar.STRING));
    }
  }

  @Nested
  @DisplayName("a comparison with null")
  class Nulls {

    @ParameterizedTest(name = "[{0}] is {1}")
    @CsvSource(delimiterString = " ~ ", quoteCharacter = '`', textBlock = """
        expire_time == null ~ (! (has expire_time))
        null != note ~ (has note)
        address == null ~ (! (has address))
        address.city != null ~ (has address.city)
        labels['tier'] == null ~ (! (has labels['tier']))
        """)
    @DisplayName("tests if the value is set")
    void testsPresence(String text, String tree) {
      assertThat(condition(text)).isEqualTo(tree);
    }

    @Test
    @DisplayName("tests a field of a related row inside its row")
    void testsARelatedField() {
      assertThat(show(GRAMMAR.compile("customer.vip_time == null", CelEnvironment.of(SALE), Type.Scalar.BOOL).root()))
          .isEqualTo("(! (VALUE customer (has customer/vip_time)))");
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
        null == null                     | null compared with null
        state == null                    | the field 'state' always has a value, so it is never null
        address.country != null          | the member 'country' always has a value
        tags[0] == null                  | the value always has a value
        expire_time < null               | null is supported only after == or !=
        total_cents < null               | no matching overload
        [null] == []                     | null is supported only after == or !=
        """)
    @DisplayName("refuses a value that is never null")
    void refusesAValueThatIsNeverNull(String text, String message) {
      assertThatThrownBy(() -> condition(text)).isInstanceOf(InvalidExpressionException.class)
          .hasMessageContaining(message);
    }
  }

  @Nested
  @DisplayName("a condition across edges")
  class Edges {

    private static String condition(String text) {
      return show(GRAMMAR.compile(text, CelEnvironment.of(SALE).with(PARAMS), Type.Scalar.BOOL).root());
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
    @DisplayName("gives the tree of the related rows")
    void givesTheTree(String text, String tree) {
      assertThat(condition(text)).isEqualTo(tree);
    }

    @Test
    @DisplayName("names the row that each edge starts from")
    void namesTheRowOfEachEdge() {
      Expression.Related customer = (Expression.Related) GRAMMAR.compile(
          "customer.orders.exists(o, o.customer.name == 'Ana')", CelEnvironment.of(SALE), Type.Scalar.BOOL)
          .root();
      assertThat(customer.row().from()).isEqualTo(Row.CURRENT);
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
        lines == lines                         | the edge 'lines' has many rows
        customer == customer                   | the edge 'customer' is not a value
        """)
    @DisplayName("refuses an edge as a value")
    void refusesAnEdgeAsAValue(String text, String message) {
      assertThatThrownBy(() -> condition(text)).isInstanceOf(InvalidExpressionException.class)
          .hasMessageContaining(message);
    }
  }

  @Nested
  @DisplayName("the named rows of a trigger")
  class NamedRows {

    private static String condition(String text) {
      return show(GRAMMAR.compile(text, TRIGGER, Type.Scalar.BOOL).root());
    }

    @ParameterizedTest(name = "[{0}] is {1}")
    @CsvSource(delimiterString = " ~ ", quoteCharacter = '`', textBlock = """
        new.total_cents > old.total_cents ~ (> new/total_cents old/total_cents)
        new.state != state ~ (!= new/state state)
        has(new.expire_time) ~ (has new/expire_time)
        old.expire_time == null ~ (! (has old/expire_time))
        new.address.country == 'CZ' ~ (== new/address.country 'CZ')
        """)
    @DisplayName("reads the fields of the rows")
    void readsTheFields(String text, String tree) {
      assertThat(condition(text)).isEqualTo(tree);
    }

    @Test
    @DisplayName("reads a row of another scope with the name of the scope of the expression")
    void readsAnotherScope() {
      Scope other = Scope.builder("shop.Order").column("price", Type.Scalar.DOUBLE, "price").build();
      assertThat(show(GRAMMAR.compile("new.price > 1.0 && total_cents > 0", CelEnvironment.of(ORDER).with("new",
          other), Type.Scalar.BOOL).root())).isEqualTo("(&& (> new/price 1.0) (> total_cents 0))");
    }

    @Test
    @DisplayName("names each row with its name")
    void namesEachRow() {
      Expression.BinaryOperation equals = (Expression.BinaryOperation) GRAMMAR.compile("new.state == old.state",
          TRIGGER, Type.Scalar.BOOL).root();
      assertThat(((Expression.FieldReference) equals.left()).row()).isEqualTo(new Row.Named("new"));
      assertThat(((Expression.FieldReference) equals.right()).row()).isEqualTo(new Row.Named("old"));
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
        new == old                 | the row 'new' is not a value
        new.missing == 1           | undefined field 'missing'
        has(new.state)             | has() tests only a field that can be unset
        other.state == 'OPEN'      | undeclared reference to 'other'
        """)
    @DisplayName("refuses what a row does not have")
    void refusesWhatTheRowDoesNotHave(String text, String message) {
      assertThatThrownBy(() -> condition(text)).isInstanceOf(InvalidExpressionException.class)
          .hasMessageContaining(message);
    }

    @ParameterizedTest(name = "[{0}]")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
        new.customer.name == 'Ana' | 'customer' is an edge
        has(new.customer)          | 'customer' is an edge
        """)
    @DisplayName("refuses the edges of a row")
    void refusesTheEdges(String text, String message) {
      assertThatThrownBy(() -> GRAMMAR.compile(text, CelEnvironment.of(SALE).with("new", SALE), Type.Scalar.BOOL))
          .isInstanceOf(InvalidExpressionException.class).hasMessageContaining(message);
    }

    @Test
    @DisplayName("refuses a row with the name of a field")
    void refusesTheNameOfAField() {
      assertThatThrownBy(() -> CelEnvironment.of(ORDER).with("state", ORDER))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("state");
    }
  }

  @Nested
  @DisplayName("the functions of the caller")
  class Functions {

    private final Function now = new Declared("now", new Overload.Global(List.of(), Type.Scalar.TIMESTAMP));
    private final Function absInt = new Declared("abs", new Overload.Global(List.of(Type.Scalar.INT),
        Type.Scalar.INT));
    private final Function absDouble = new Declared("abs", new Overload.Global(List.of(Type.Scalar.DOUBLE),
        Type.Scalar.DOUBLE));
    private final Function lower = new Declared("lower", new Overload.Member(Type.Scalar.STRING, List.of(),
        Type.Scalar.STRING));

    @Test
    @DisplayName("calls a declared function and no other")
    void callsADeclaredFunction() {
      assertThat(show(compile(GRAMMAR.with(now), "expire_time > now()", Type.Scalar.BOOL).root()))
          .isEqualTo("(> expire_time (now))");
      assertThatThrownBy(() -> compile("expire_time > now()", Type.Scalar.BOOL))
          .isInstanceOf(InvalidExpressionException.class).hasMessageContaining("undeclared reference to 'now'");
    }

    @Test
    @DisplayName("calls the overload that the arguments fit")
    void callsTheOverloadThatTheArgumentsFit() {
      CelGrammar grammar = GRAMMAR.with(absInt).with(absDouble);
      assertThat(((Expression.FunctionCall) compile(grammar, "abs(total_cents)", Type.Scalar.INT).root()).function())
          .isSameAs(absInt);
      assertThat(((Expression.FunctionCall) compile(grammar, "abs(rate)", Type.Scalar.DOUBLE).root()).function())
          .isSameAs(absDouble);
      assertThatThrownBy(() -> compile(grammar, "abs(state) > 0", Type.Scalar.BOOL))
          .isInstanceOf(InvalidExpressionException.class).hasMessageContaining("no matching overload");
    }

    @Test
    @DisplayName("calls a member function on its value")
    void callsAMemberFunction() {
      assertThat(show(compile(GRAMMAR.with(lower), "state.lower() == 'open'", Type.Scalar.BOOL).root()))
          .isEqualTo("(== (lower state) 'open')");
    }

    @Test
    @DisplayName("refuses a function that the grammar has")
    void refusesAFunctionTwice() {
      assertThatThrownBy(() -> GRAMMAR.with(now).with(new Declared("now", now.overload())))
          .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("now");
    }
  }

  @Nested
  @DisplayName("the operations and macros of the caller")
  class Extensions {

    private record Positive() implements Operation {

      @Override
      public String function() {
        return "positive";
      }

      @Override
      public List<Overload> overloads() {
        return List.of(new Overload.Global(List.of(Type.Scalar.INT), Type.Scalar.BOOL));
      }

      @Override
      public Expression translate(Call call, Next next) {
        if (!(call.argument(0) instanceof Expression.FieldReference field)) {
          throw call.refuse(0, "positive() takes a field");
        }
        return new Expression.BinaryOperation(Expression.Operator.GREATER, field,
            new Expression.Literal(Type.Scalar.INT, 0L, call.position()), Type.Scalar.BOOL, call.position());
      }
    }

    private record Between() implements Macro {

      @Override
      public String function() {
        return "between";
      }

      @Override
      public int arguments() {
        return 3;
      }

      @Override
      public boolean member() {
        return false;
      }

      @Override
      public Optional<Syntax> expand(MacroCall call) {
        return Optional.of(call.call("_&&_", call.call("_>=_", call.argument(0), call.argument(1)),
            call.call("_<=_", call.argument(0), call.argument(2))));
      }
    }

    @Test
    @DisplayName("translates a call with an operation of the caller")
    void translatesWithAnOperation() {
      CelGrammar grammar = GRAMMAR.with(new Positive());
      assertThat(show(compile(grammar, "positive(total_cents)", Type.Scalar.BOOL).root()))
          .isEqualTo("(> total_cents 0)");
      assertThatThrownBy(() -> compile(grammar, "positive(1)", Type.Scalar.BOOL))
          .isInstanceOf(InvalidExpressionException.class).hasMessageContaining("positive() takes a field");
    }

    @Test
    @DisplayName("rewrites a call with a macro of the caller")
    void rewritesWithAMacro() {
      assertThat(show(compile(GRAMMAR.with(new Between()), "between(total_cents, 1, 5)", Type.Scalar.BOOL).root()))
          .isEqualTo("(&& (>= total_cents 1) (<= total_cents 5))");
    }

    private record Guard() implements Operation {

      @Override
      public String function() {
        return "_==_";
      }

      @Override
      public List<Overload> overloads() {
        return List.of();
      }

      @Override
      public Expression translate(Call call, Next next) {
        if (call.argument(0) instanceof Expression.FieldReference field && field.field().name().equals("note")) {
          throw call.refuse(0, "the note cannot be compared");
        }
        return next.translate(call);
      }
    }

    @Test
    @DisplayName("decorates an operation of the grammar with an operation of the caller")
    void decoratesAnOperation() {
      CelGrammar grammar = GRAMMAR.with(new Guard());
      assertThat(show(compile(grammar, "state == 'OPEN'", Type.Scalar.BOOL).root())).isEqualTo("(== state 'OPEN')");
      assertThat(show(compile(grammar, "expire_time == null", Type.Scalar.BOOL).root())).isEqualTo("(! (has expire_time))");
      assertThatThrownBy(() -> compile(grammar, "note == 'x'", Type.Scalar.BOOL))
          .isInstanceOf(InvalidExpressionException.class).hasMessageContaining("the note cannot be compared");
    }

    @Test
    @DisplayName("rewrites the nodes with a decorator of the caller")
    void rewritesWithADecorator() {
      CelGrammar grammar = GRAMMAR.with(Decorator.of(Expression.Literal.class,
          literal -> literal.value() instanceof String text
              ? new Expression.Literal(literal.type(), text.toLowerCase(Locale.ROOT), literal.position())
              : literal));
      assertThat(show(compile(grammar, "state in ['OPEN', 'PAID'] && total_cents > 1", Type.Scalar.BOOL).root()))
          .isEqualTo("(&& (in state ['open' 'paid']) (> total_cents 1))");
    }

    @ParameterizedTest(name = "[{0}]")
    @ValueSource(strings = { "labels['tier'] == 'gold'", "labels.tier == 'gold'", "has(labels.tier)", "has(note)",
        "note == 'x'" })
    @DisplayName("passes every field that an expression reads through a decorator of the caller")
    void decoratesEveryField(String text) {
      CelGrammar grammar = GRAMMAR.with(Decorator.of(Expression.FieldReference.class, field -> {
        if (Set.of("labels", "note").contains(field.field().name())) {
          throw new InvalidExpressionException(field.position(), "the field %s is private", field.field().name());
        }
        return field;
      }));
      assertThatThrownBy(() -> compile(grammar, text, Type.Scalar.BOOL)).isInstanceOf(InvalidExpressionException.class)
          .hasMessageContaining("is private");
    }

    @Test
    @DisplayName("refuses a call whose operation the grammar does not have")
    void refusesWithoutAnOperation() {
      assertThatThrownBy(() -> compile(GRAMMAR.without(Conditional.class), "paid ? true : false",
          Type.Scalar.BOOL)).isInstanceOf(InvalidExpressionException.class)
          .hasMessageContaining("the operator '_?_:_' is not supported");
    }
  }

  @Nested
  @DisplayName("an expression that SQL cannot answer")
  class Refusals {

    @ParameterizedTest(name = "[{0}]")
    @CsvSource(delimiter = '|', quoteCharacter = '`', textBlock = """
        state == 1                                 | no matching overload
        unknown == 1                               | undeclared reference to 'unknown'
        params.missing == 1                        | undefined field 'missing'
        state.matches('O.*')                       | matches() is not supported
        size(tags) > 1                             | size() is not supported
        int(rate) > 1                              | int() is not supported
        total_cents == 1u                          | applied to '(int, uint)'
        total_cents / params.min > 1               | must be a constant that is not zero
        total_cents % 0 == 1                       | must be a constant that is not zero
        has(state)                                 | has() tests only a field that can be unset
        has(address.country)                       | the member 'country' always has a value
        tags.exists_one(t, t == 'a')               | undeclared reference to 'exists_one'
        tags.map(t, t) == ['a']                    | undeclared reference to 'map'
        ['a', 'b'].exists(t, t == state)           | supported only over a list field
        tags[total_cents] == 'a'                   | must be a constant or a parameter
        state in labels                            | a key 'in' a map must be a constant or a parameter
        {'a': 1}['a'] == 1                         | a map literal is not supported
        int == int                                 | 'int' is not a field
        create_time > timestamp('yesterday')       | timestamp
        create_time > timestamp(state)             | expected a string constant in timestamp()
        """)
    @DisplayName("tells what is not supported")
    void tellsWhat(String text, String message) {
      assertThatThrownBy(() -> condition(text))
          .isInstanceOf(InvalidExpressionException.class)
          .hasMessageContaining(message)
          .extracting(error -> ((InvalidQueryException) error).field())
          .isEqualTo(InvalidQueryException.EXPRESSION);
    }

    @Test
    @DisplayName("refuses an expression of another type than the caller expects")
    void refusesAnotherType() {
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
    @DisplayName("tells where in the text the problem is")
    void tellsWhere(String text, String character) {
      assertThatThrownBy(() -> condition(text))
          .isInstanceOfSatisfying(InvalidExpressionException.class,
              error -> assertThat(text.substring(error.position(), error.position() + 1)).isEqualTo(character));
    }
  }

  private static String show(Expression expression) {
    return switch (expression) {
      case Expression.Literal literal when literal.value() instanceof String text -> "'" + text + "'";
      case Expression.Literal literal -> String.valueOf(literal.value());
      case Expression.Parameter param -> "$" + param.name();
      case Expression.FieldReference field -> show(field.row()) + field.field().name();
      case Expression.Variable variable -> variable.name();
      case Expression.MemberAccess member -> show(member.operand()) + "." + member.member().name();
      case Expression.IndexAccess index -> show(index.operand()) + "[" + show(index.key()) + "]";
      case Expression.Presence has -> "(has " + show(has.operand()) + ")";
      case Expression.Not not -> "(! " + show(not.operand()) + ")";
      case Expression.Negative negate -> "(- " + show(negate.operand()) + ")";
      case Expression.Conditional conditional -> "(? " + show(conditional.condition()) + " " + show(conditional.then())
          + " " + show(conditional.otherwise()) + ")";
      case Expression.BinaryOperation binary -> "(" + symbol(binary.operator()) + " " + show(binary.left()) + " "
          + show(binary.right()) + ")";
      case Expression.Membership in -> "(in " + show(in.element()) + " " + show(in.collection()) + ")";
      case Expression.ListLiteral list -> list.elements().stream().map(CelGrammarTest::show)
          .collect(Collectors.joining(" ", "[", "]"));
      case Expression.StringMatch match -> "(" + match.kind() + " " + show(match.string()) + " " + show(match.part())
          + ")";
      case Expression.FunctionCall call -> "(" + call.function().name()
          + call.arguments().stream().map(argument -> " " + show(argument)).collect(Collectors.joining()) + ")";
      case Expression.Quantifier quantifier -> "(" + quantifier.kind() + " " + quantifier.variable().name() + " "
          + show(quantifier.range()) + " " + show(quantifier.predicate()) + ")";
      case Expression.Related.Value related -> "(VALUE " + related.row().name() + " " + show(related.body()) + ")";
      case Expression.Related.Exists related -> "(EXISTS " + related.row().name() + " " + show(related.body()) + ")";
      case Expression.Related.All related -> "(ALL " + related.row().name() + " " + show(related.body()) + ")";
    };
  }

  private static String show(Row row) {
    return switch (row) {
      case Row.Current _ -> "";
      case Row.Related related -> related.name() + "/";
      case Row.Named named -> named.name() + "/";
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
