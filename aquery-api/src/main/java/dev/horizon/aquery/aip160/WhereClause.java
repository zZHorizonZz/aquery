package dev.horizon.aquery.aip160;

import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.aip160.FieldBackend.ImplicitRestrictionContext;
import dev.horizon.aquery.aip160.FieldBackend.RestrictionContext;
import dev.horizon.aquery.aip160.Filter.Expression;
import dev.horizon.aquery.aip160.Filter.Factor;
import dev.horizon.aquery.aip160.Filter.Member;
import dev.horizon.aquery.aip160.Filter.Restriction;
import dev.horizon.aquery.aip160.Filter.Sequence;
import dev.horizon.aquery.aip160.Filter.Simple;
import dev.horizon.aquery.aip160.Filter.Term;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes an ISO SQL WHERE clause from a table schema and a parsed AIP-160 filter.
 *
 * <p>
 * The clause is a boolean SQL expression in parentheses. It does not include the {@code WHERE} keyword. An empty
 * filter compiles to {@code (1 = 1)} and binds no values.
 *
 * <p>
 * The compiler works in these steps:
 *
 * <ul>
 * <li>The compiler finds the field that each restriction names. It refuses the restriction if the backend does
 * not support the operator. It also refuses a dot into a field that has no members.
 * <li>The compiler sends each accepted restriction to the backend of the field. The backend writes the SQL.
 * <li>A restriction can go into a field, as in {@code labels.site}. The compiler gives the extra segments to the
 * backend of the field.
 * <li>A restriction without a field name, as in {@code prod}, goes to all fields for implicit filters. The
 * compiler puts OR between their answers. If the schema has no such field, the compiler refuses the filter.
 * <li>The compiler puts AND between expressions, OR between factors and NOT before negations. It follows the
 * shape of the filter tree.
 * </ul>
 *
 * <p>
 * The output is safe against SQL injection because of its design. The column names come from the schema. All
 * values from the client are bound values. Thus the client text never becomes part of the SQL text.
 *
 * <p>
 * The clause binds its values in the {@link Parameters} of the statement, in the sequence of their placeholders in
 * the SQL text. Use the same parameters for the other clauses of the statement, for example a keyset. Then the
 * placeholders do not collide. The table alias must be an SQL identifier, as {@link TableAlias} specifies.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip160/filter_generator.go">LUCI aip160: filter_generator.go (WhereClause)</a>
 */
public final class WhereClause {

  private final DatabaseTable table;
  private final SqlGenerator generator;

  private WhereClause(DatabaseTable table, SqlGenerator generator) {
    this.table = table;
    this.generator = generator;
  }

  /**
   * Compiles the filter for the table.
   *
   * @param table the schema of the table for the filter
   * @param filter the parsed filter. It can be null or empty
   * @param tableAlias the alias of the table in the statement, for example in a JOIN. Use null or an empty string if the table
   * has no alias
   * @param parameters the parameters of the statement. The clause binds its values in them
   * @return the boolean SQL expression in parentheses
   * @throws InvalidFilterException if the filter does not compile for this schema
   * @throws IllegalArgumentException if the table alias starts with {@code aquery_}, or if it is not an SQL identifier
   */
  public static String of(DatabaseTable table, Filter filter, String tableAlias, Parameters parameters) {
    SqlGenerator generator = new SqlGenerator(tableAlias, parameters);
    if (filter == null || filter.expression() == null) {
      return "(1 = 1)";
    }
    return new WhereClause(table, generator).expressionQuery(filter.expression());
  }

  private String expressionQuery(Expression expression) {
    List<String> factors = new ArrayList<>();
    // A sequence and a factor both have the meaning of AND of their parts. The compiler uses exact match
    // semantics. It does not rank the rows by the number of matching factors.
    for (Sequence sequence : expression.sequences()) {
      for (Factor factor : sequence.factors()) {
        factors.add(factorQuery(factor));
      }
    }
    return joined(factors, " AND ");
  }

  private String factorQuery(Factor factor) {
    List<String> terms = new ArrayList<>();
    for (Term term : factor.terms()) {
      terms.add(termQuery(term));
    }
    return joined(terms, " OR ");
  }

  private String termQuery(Term term) {
    String simpleQuery = simpleQuery(term.simple());
    return term.negated() ? "(NOT " + simpleQuery + ")" : simpleQuery;
  }

  private String simpleQuery(Simple simple) {
    if (simple.restriction() != null) {
      return restrictionQuery(simple.restriction());
    }
    if (simple.composite() != null) {
      return expressionQuery(simple.composite());
    }
    throw new IllegalStateException("invalid 'simple' clause in query filter");
  }

  private String restrictionQuery(Restriction restriction) {
    if (restriction.comparable() == null || restriction.comparable().member() == null) {
      throw new IllegalStateException("invalid comparable");
    }
    Member member = restriction.comparable().member();
    if (restriction.operator() == null) {
      return implicitRestrictionQuery(member);
    }

    DatabaseTable.FilterableField filterable = table.filterableFieldByFieldPath(fieldPathOf(member));
    Field field = filterable.field();
    FieldBackend backend = field.backend();
    if (!filterable.unusedPath().isEmpty() && !backend.acceptsNestedFields()) {
      throw new FieldsUnsupportedException(field.fieldPath());
    }
    if (!backend.operators().contains(restriction.operator())) {
      throw new OperatorNotImplementedException(restriction.operator(), field.fieldPath(), backend.typeName());
    }
    RestrictionContext context = new RestrictionContext(field.fieldPath(), filterable.unusedPath(), restriction.operator(),
        restriction.arg());
    return backend.restrictionQuery(context, generator);
  }

  private String implicitRestrictionQuery(Member member) {
    if (!member.fields().isEmpty()) {
      throw new InvalidFilterException("fields are not allowed without an operator, try wrapping '%s' in double quotes: '%s'",
          member.input(), member.input());
    }
    // A value without a field name can have quotes or no quotes.
    String value = member.value().value();
    List<String> clauses = new ArrayList<>();
    for (Field field : table.implicitFields()) {
      try {
        clauses.add(field.backend().implicitRestrictionQuery(new ImplicitRestrictionContext(value), generator));
      } catch (InvalidFilterException refused) {
        throw new InvalidFilterException("implicit restriction on field '%s': %s", field.fieldPath(), refused.getMessage());
      }
    }
    if (clauses.isEmpty()) {
      throw new InvalidFilterException("no fields are configured to match the bare value '%s'", value);
    }
    return "(" + String.join(" OR ", clauses) + ")";
  }

  private static FieldPath fieldPathOf(Member member) {
    if (member.value().quoted()) {
      throw new InvalidFilterException(
          "expected a field name on the left hand side of a restriction, got the string literal '%s'", member.input());
    }
    List<String> segments = new ArrayList<>(member.fields().size() + 1);
    segments.add(member.value().value());
    member.fields().forEach(field -> segments.add(field.value()));
    return new FieldPath(segments);
  }

  private static String joined(List<String> parts, String operator) {
    return parts.size() == 1 ? parts.getFirst() : "(" + String.join(operator, parts) + ")";
  }
}
