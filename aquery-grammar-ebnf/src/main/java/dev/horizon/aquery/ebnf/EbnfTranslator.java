package dev.horizon.aquery.ebnf;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.aip160.Filter.And;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Global;
import dev.horizon.aquery.aip160.Filter.Not;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.aip160.Filter.StringLiteral;
import dev.horizon.aquery.aip160.Filter.Text;
import dev.horizon.aquery.aip160.FilterTranslator;
import dev.horizon.aquery.aip160.InvalidFilterException;
import dev.horizon.aquery.ebnf.Ast.Expression;
import dev.horizon.aquery.ebnf.Ast.Factor;
import dev.horizon.aquery.ebnf.Ast.Member;
import dev.horizon.aquery.ebnf.Ast.Restriction;
import dev.horizon.aquery.ebnf.Ast.Sequence;
import dev.horizon.aquery.ebnf.Ast.Simple;
import dev.horizon.aquery.ebnf.Ast.Term;
import java.util.ArrayList;
import java.util.List;

/**
 * Changes the AIP-160 syntax tree into the {@link Filter} tree of the api.
 *
 * <p>
 * The change follows these rules:
 *
 * <ul>
 * <li>An expression and a sequence are both an {@link And} of their factors. The compiler uses exact match
 * semantics. Thus a sequence has the same meaning as AND.
 * <li>A factor is an {@link Or} of its terms. A negated term is a {@link Not}. A list with one item is the item.
 * <li>A restriction with an operator is a {@link Filter.Restriction}. A restriction without an operator is a
 * {@link Global}.
 * <li>A value in quotes is a {@link StringLiteral} with wildcards. A different value is a {@link Text}. A text
 * keeps its dots, as in {@code 1.5s}.
 * </ul>
 *
 * <p>
 * The change refuses three forms that the grammar permits but no field can answer: a string on the left side of a
 * restriction, a global restriction with dots, and an expression in parentheses as an argument.
 */
final class EbnfTranslator implements FilterTranslator<Ast.Root> {

  @Override
  public Filter translate(Ast.Root root) {
    return new Filter(root.expression() == null ? null : expression(root.expression()));
  }

  private Condition expression(Expression expression) {
    List<Condition> factors = new ArrayList<>();
    for (Sequence sequence : expression.sequences()) {
      for (Factor factor : sequence.factors()) {
        factors.add(factor(factor));
      }
    }
    return factors.size() == 1 ? factors.getFirst() : new And(factors);
  }

  private Condition factor(Factor factor) {
    List<Condition> terms = new ArrayList<>(factor.terms().size());
    for (Term term : factor.terms()) {
      terms.add(term(term));
    }
    return terms.size() == 1 ? terms.getFirst() : new Or(terms);
  }

  private Condition term(Term term) {
    Condition simple = simple(term.simple());
    return term.negated() ? new Not(simple) : simple;
  }

  private Condition simple(Simple simple) {
    if (simple.restriction() != null) {
      return restriction(simple.restriction());
    }
    if (simple.composite() != null) {
      return expression(simple.composite());
    }
    throw new IllegalStateException("invalid 'simple' clause in query filter");
  }

  private Condition restriction(Restriction restriction) {
    if (restriction.comparable() == null || restriction.comparable().member() == null) {
      throw new IllegalStateException("invalid comparable");
    }
    Member member = restriction.comparable().member();
    if (restriction.operator() == null) {
      return global(member);
    }
    FieldPath fieldPath = fieldPath(member);
    if (restriction.arg().composite() != null) {
      throw new InvalidFilterException("argument for field '%s': composite expressions in arguments not supported yet",
          fieldPath);
    }
    return new Filter.Restriction(fieldPath, restriction.operator(), value(restriction.arg().comparable().member()));
  }

  private Global global(Member member) {
    if (!member.fields().isEmpty()) {
      throw new InvalidFilterException("fields are not allowed without an operator, try wrapping '%s' in double quotes: '%s'",
          member.input(), member.input());
    }
    // A value without a field name can have quotes or no quotes.
    return new Global(member.value().value());
  }

  private FieldPath fieldPath(Member member) {
    if (member.value().quoted()) {
      throw new InvalidFilterException(
          "expected a field name on the left hand side of a restriction, got the string literal '%s'", member.input());
    }
    List<String> segments = new ArrayList<>(member.fields().size() + 1);
    segments.add(member.value().value());
    member.fields().forEach(field -> segments.add(field.value()));
    return new FieldPath(segments);
  }

  private Filter.Value value(Member member) {
    if (member.value().quoted() && member.fields().isEmpty()) {
      return new StringLiteral(member.value().value(), true);
    }
    return new Text(member.input());
  }
}
