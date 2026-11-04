package dev.horizon.aquery.cel.filter;

import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Operator;
import dev.horizon.aquery.cel.Has;
import dev.horizon.aquery.cel.Macro;
import dev.horizon.aquery.cel.filter.operation.Comparison;
import dev.horizon.aquery.cel.filter.operation.Conjunction;
import dev.horizon.aquery.cel.filter.operation.Disjunction;
import dev.horizon.aquery.cel.filter.operation.Membership;
import dev.horizon.aquery.cel.filter.operation.Negation;
import dev.horizon.aquery.cel.filter.operation.Refused;
import dev.horizon.aquery.cel.filter.operation.StringMatch;
import java.util.ArrayList;
import java.util.List;

/**
 * The CEL that a filter can be written in: the macros that rewrite its syntax, the operations that translate its
 * calls into conditions, and the decorator that rewrites the conditions that they give.
 *
 * <p>
 * The {@linkplain #standard() standard} grammar has {@code && || !}, the comparisons of a field and a constant,
 * {@code in}, {@code contains}, {@code startsWith}, {@code endsWith} and {@code has()}. A {@link CelFilterParser}
 * parses text with a grammar:
 *
 * <pre>{@code
 * FilterParser parser = new CelFilterParser(CelFilterGrammar.standard().without(StringMatch.class));
 * }</pre>
 *
 * <p>
 * A grammar cannot change. It is safe for use by several threads at the same time.
 */
public final class CelFilterGrammar {

  private final List<Operation> operations;
  private final List<Macro> macros;
  private final Decorator decorator;

  private CelFilterGrammar(List<Operation> operations, List<Macro> macros, Decorator decorator) {
    this.operations = List.copyOf(operations);
    this.macros = List.copyOf(macros);
    this.decorator = decorator;
  }

  /**
   * Gives the grammar of every filter operation and macro that aquery has.
   *
   * @return the grammar
   */
  public static CelFilterGrammar standard() {
    return new CelFilterGrammar(List.of(
        new Conjunction(),
        new Disjunction(),
        new Negation(),
        new Comparison("_==_", Operator.EQUALS, Operator.EQUALS),
        new Comparison("_!=_", Operator.NOT_EQUALS, Operator.NOT_EQUALS),
        new Comparison("_<_", Operator.LESS_THAN, Operator.GREATER_THAN),
        new Comparison("_<=_", Operator.LESS_EQUALS, Operator.GREATER_EQUALS),
        new Comparison("_>_", Operator.GREATER_THAN, Operator.LESS_THAN),
        new Comparison("_>=_", Operator.GREATER_EQUALS, Operator.LESS_EQUALS),
        new Membership("@in"),
        new Membership("_in_"),
        new StringMatch("contains", Operator.HAS),
        new StringMatch("startsWith", Operator.STARTS_WITH),
        new StringMatch("endsWith", Operator.ENDS_WITH),
        new Refused("_?_:_", "the conditional operator '? :' is not supported in a filter"),
        new Refused("matches", "matches() is not supported in a filter, because ISO SQL has no portable"
            + " regular expressions")),
        List.of(new Has()), Decorator.none());
  }

  /**
   * Gives this grammar with one more operation, which comes before the operations of its function in this grammar.
   *
   * @param operation the operation
   * @return the grammar
   */
  public CelFilterGrammar with(Operation operation) {
    List<Operation> all = new ArrayList<>();
    all.add(operation);
    all.addAll(operations);
    return new CelFilterGrammar(all, macros, decorator);
  }

  /**
   * Gives this grammar with one more macro.
   *
   * @param macro the macro
   * @return the grammar
   */
  public CelFilterGrammar with(Macro macro) {
    List<Macro> all = new ArrayList<>(macros);
    all.add(macro);
    return new CelFilterGrammar(operations, all, decorator);
  }

  /**
   * Gives this grammar with one more decorator, which rewrites the conditions after the decorators of this grammar.
   *
   * @param decorator the decorator
   * @return the grammar
   */
  public CelFilterGrammar with(Decorator decorator) {
    return new CelFilterGrammar(operations, macros, this.decorator.then(decorator));
  }

  /**
   * Gives this grammar without the operations of one class. A call that only they translate is refused.
   *
   * @param kind the class, for example {@code StringMatch.class}
   * @return the grammar
   */
  public CelFilterGrammar without(Class<? extends Operation> kind) {
    return new CelFilterGrammar(operations.stream().filter(operation -> !kind.isInstance(operation)).toList(),
        macros, decorator);
  }

  List<Macro> macros() {
    return macros;
  }

  Next chain(String function, Next last) {
    return new Chain(operations.stream().filter(operation -> operation.function().equals(function)).toList(),
        last);
  }

  Condition decorate(Condition condition) {
    return decorator.decorate(condition);
  }
}
