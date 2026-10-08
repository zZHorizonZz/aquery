package dev.horizon.aquery.aip160;

import dev.horizon.aquery.Parameters;
import dev.horizon.aquery.aip160.FieldBackend.ImplicitRestrictionContext;
import dev.horizon.aquery.aip160.FieldBackend.RestrictionContext;
import dev.horizon.aquery.aip160.Filter.And;
import dev.horizon.aquery.aip160.Filter.Condition;
import dev.horizon.aquery.aip160.Filter.Global;
import dev.horizon.aquery.aip160.Filter.Not;
import dev.horizon.aquery.aip160.Filter.Or;
import dev.horizon.aquery.aip160.Filter.Restriction;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes an ISO SQL WHERE clause from a table schema and a parsed filter.
 *
 * <p>
 * The clause is a boolean SQL expression in parentheses. It does not include the {@code WHERE} keyword. An empty
 * filter compiles to {@code (1 = 1)} and binds no values. The language of the filter text has no effect on the
 * compiler, because all parsers give the same {@link Filter} tree.
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
 * <li>The compiler puts AND, OR and NOT between the conditions. It follows the shape of the filter tree.
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
    if (filter == null || filter.condition() == null) {
      return "(1 = 1)";
    }
    return new WhereClause(table, generator).query(filter.condition());
  }

  private String query(Condition condition) {
    // The compiler uses exact match semantics. It does not rank the rows by the number of matching conditions.
    return switch (condition) {
      case And and -> joined(and.operands(), " AND ");
      case Or or -> joined(or.operands(), " OR ");
      case Not not -> "(NOT " + query(not.operand()) + ")";
      case Restriction restriction -> restrictionQuery(restriction);
      case Global global -> implicitRestrictionQuery(global.value());
    };
  }

  private String restrictionQuery(Restriction restriction) {
    DatabaseTable.FilterableField filterable = table.filterableFieldByFieldPath(restriction.fieldPath());
    Field field = filterable.field();
    FieldBackend backend = field.backend();
    if (!filterable.unusedPath().isEmpty() && !backend.acceptsNestedFields()) {
      throw new FieldsUnsupportedException(field.fieldPath());
    }
    if (!backend.operators().contains(restriction.operator())) {
      throw new OperatorNotImplementedException(restriction.operator(), field.fieldPath(), backend.typeName());
    }
    RestrictionContext context = new RestrictionContext(field.fieldPath(), filterable.unusedPath(), restriction.operator(),
        restriction.value());
    return backend.restrictionQuery(context, generator);
  }

  private String implicitRestrictionQuery(String value) {
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

  private String joined(List<Condition> operands, String operator) {
    if (operands.size() == 1) {
      return query(operands.getFirst());
    }
    List<String> parts = new ArrayList<>(operands.size());
    for (Condition operand : operands) {
      parts.add(query(operand));
    }
    return "(" + String.join(operator, parts) + ")";
  }
}
