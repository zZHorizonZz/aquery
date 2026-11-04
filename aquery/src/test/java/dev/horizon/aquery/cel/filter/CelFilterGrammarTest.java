package dev.horizon.aquery.cel.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter.And;
import dev.horizon.aquery.aip160.Filter.BoolLiteral;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.IntLiteral;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Filter.StringLiteral;
import dev.horizon.aquery.aip160.Filter.Text;
import dev.horizon.aquery.aip160.Filter.Value;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.cel.Macro;
import dev.horizon.aquery.cel.MacroCall;
import dev.horizon.aquery.cel.Syntax;
import dev.horizon.aquery.cel.filter.operation.StringMatch;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CelFilterGrammarTest {

  private static Condition parse(CelFilterGrammar grammar, String text) {
    return new CelFilterParser(grammar).parse(text).condition();
  }

  private static Restriction restriction(String field, Operator operator, Value value) {
    return new Restriction(new FieldPath(field), operator, value);
  }

  private record Mine() implements Operation {

    @Override
    public String function() {
      return "mine";
    }

    @Override
    public Condition translate(Call call, Next next) {
      if (!call.arguments().isEmpty()) {
        throw call.refuse("mine() takes no arguments");
      }
      return restriction("owner", Operator.EQUALS, new Text("me"));
    }
  }

  private record Guard() implements Operation {

    @Override
    public String function() {
      return "_==_";
    }

    @Override
    public Condition translate(Call call, Next next) {
      if (call.argument(0).isField() && call.argument(0).path().equals(new FieldPath("secret"))) {
        throw call.argument(0).refuse("the secret cannot be filtered");
      }
      return next.translate(call);
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
  @DisplayName("tests if a field of the row is set with has()")
  void testsAFieldWithHas() {
    assertThat(parse(CelFilterGrammar.standard(), "has(note)")).isEqualTo(restriction("note", Operator.HAS,
        new Text("*")));
  }

  @Test
  @DisplayName("translates a call with an operation of the caller")
  void translatesWithAnOperation() {
    CelFilterGrammar grammar = CelFilterGrammar.standard().with(new Mine());
    assertThat(parse(grammar, "mine() && count > 1")).isEqualTo(new And(List.of(restriction("owner",
        Operator.EQUALS, new Text("me")), restriction("count", Operator.GREATER_THAN, new IntLiteral(1)))));
    assertThatThrownBy(() -> parse(grammar, "mine(1)")).isInstanceOf(InvalidFilterException.class)
        .hasMessageContaining("mine() takes no arguments");
  }

  @Test
  @DisplayName("decorates an operation of the grammar with an operation of the caller")
  void decoratesAnOperation() {
    CelFilterGrammar grammar = CelFilterGrammar.standard().with(new Guard());
    assertThat(parse(grammar, "name == \"a\"")).isEqualTo(restriction("name", Operator.EQUALS,
        new StringLiteral("a", false)));
    assertThatThrownBy(() -> parse(grammar, "secret == \"a\"")).isInstanceOf(InvalidFilterException.class)
        .hasMessageContaining("the secret cannot be filtered");
  }

  @Test
  @DisplayName("rewrites a call with a macro of the caller")
  void rewritesWithAMacro() {
    assertThat(parse(CelFilterGrammar.standard().with(new Between()), "between(count, 1, 5)")).isEqualTo(new And(
        List.of(restriction("count", Operator.GREATER_EQUALS, new IntLiteral(1)), restriction("count",
            Operator.LESS_EQUALS, new IntLiteral(5)))));
  }

  @ParameterizedTest(name = "[{0}]")
  @MethodSource("decorated")
  @DisplayName("rewrites the conditions with a decorator of the caller")
  void rewritesWithADecorator(String text, Condition expected) {
    CelFilterGrammar grammar = CelFilterGrammar.standard().with(Decorator.of(Restriction.class,
        restriction -> restriction.fieldPath().equals(new FieldPath("count"))
            ? new Restriction(new FieldPath("total"), restriction.operator(), restriction.value())
            : restriction));
    assertThat(parse(grammar, text)).isEqualTo(expected);
  }

  static Stream<Arguments> decorated() {
    return Stream.of(
        arguments("count > 1 && flag", new And(List.of(restriction("total", Operator.GREATER_THAN, new IntLiteral(1)),
            restriction("flag", Operator.EQUALS, new BoolLiteral(true))))),
        arguments("count in [1]", restriction("total", Operator.EQUALS, new IntLiteral(1))),
        arguments("count in [1, 2]", new Or(List.of(restriction("total", Operator.EQUALS, new IntLiteral(1)),
            restriction("total", Operator.EQUALS, new IntLiteral(2))))));
  }

  @Test
  @DisplayName("refuses a call whose operation the grammar does not have")
  void refusesWithoutAnOperation() {
    assertThatThrownBy(() -> parse(CelFilterGrammar.standard().without(StringMatch.class),
        "name.startsWith(\"a\")")).isInstanceOf(InvalidFilterException.class)
        .hasMessageContaining("the function 'startsWith' is not supported in a filter");
  }
}
