package dev.horizon.aquery.cel;

import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.shaded.cel.common.CelAbstractSyntaxTree;
import dev.horizon.aquery.shaded.cel.common.CelFunctionDecl;
import dev.horizon.aquery.shaded.cel.common.CelIssue;
import dev.horizon.aquery.shaded.cel.common.CelOptions;
import dev.horizon.aquery.shaded.cel.common.CelOverloadDecl;
import dev.horizon.aquery.shaded.cel.common.CelValidationException;
import dev.horizon.aquery.shaded.cel.common.CelValidationResult;
import dev.horizon.aquery.shaded.cel.common.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.types.CelType;
import dev.horizon.aquery.shaded.cel.common.types.StructType;
import dev.horizon.aquery.shaded.cel.compiler.CelCompiler;
import dev.horizon.aquery.shaded.cel.compiler.CelCompilerBuilder;
import dev.horizon.aquery.shaded.cel.compiler.CelCompilerFactory;
import dev.horizon.aquery.shaded.cel.parser.CelMacro;
import dev.horizon.aquery.shaded.cel.parser.CelStandardMacro;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The compiler of CEL expressions.
 *
 * <p>
 * The compiler parses the text with cel-java, and checks it with the type checker of cel-java against the fields of
 * the scope and the types of the parameters. Then it changes the checked tree into an {@link Expression}. The
 * expression can use:
 *
 * <ul>
 * <li>The fields of the scope by their names, as in {@code total_cents > 100}, and the parameters as
 * {@code params.name}.
 * <li>{@code &&}, {@code ||}, {@code !}, the comparisons {@code == != < <= > >=}, and {@code in} with a list, a
 * list parameter, or a list or map field.
 * <li>{@code + - *} on numbers, {@code /} and {@code %} with a constant divisor that is not zero, {@code +} on
 * strings, a timestamp plus or minus a duration, and the duration between two timestamps.
 * <li>Members of structs ({@code address.country}), keys of maps ({@code labels['tier']}) and elements of lists
 * ({@code tags[0]}).
 * <li>{@code has()} on a field that can be NULL, on an optional member and on a key: {@code has(expire_time)},
 * {@code has(address.city)}, {@code has(labels.tier)}.
 * <li>{@code startsWith}, {@code endsWith} and {@code contains} on strings.
 * <li>{@code exists} and {@code all} over a list field, as in {@code items.exists(i, i.quantity > 1)}.
 * <li>The edges of the scope: the fields of a related row, as in {@code customer.address.country}, {@code has()} of an
 * edge, and {@code exists} and {@code all} over the related rows of an edge to many rows. They give
 * {@link Expression.Related} nodes.
 * <li>{@code timestamp('...')} and {@code duration('...')} of a constant.
 * <li>The functions that the caller declares, such as {@code now()}: each is a {@link Expression.FunctionCall} of
 * the {@link Function} that the caller gave.
 * </ul>
 *
 * <p>
 * It refuses the rest of CEL: {@code matches()}, {@code size()}, conversions such as {@code int()}, the conditional
 * operator, {@code null}, unsigned integers, maps and messages as literals, and the macros {@code map},
 * {@code filter} and {@code exists_one}. The error tells what is not supported, and where.
 *
 * <p>
 * Make one compiler and use it for all expressions. It is safe for use by several threads at the same time.
 */
public final class CelExpressionCompiler {

  /** The hidden variable that holds the row, for {@code has()} on a field. */
  static final String RECORD = "aquery__record";

  /** The variable of the parameters. */
  static final String PARAMS = "params";

  private static final String PARAMS_TYPE = "aquery.Params";

  private static final CelMacro HAS = CelMacro.newGlobalMacro("has", 1, (factory, target, arguments) -> {
    CelExpr argument = arguments.getFirst();
    return Optional.of(switch (argument.exprKind().getKind()) {
      // A field of the row is a variable, and CEL's has() takes only a member. Thus has(f) becomes has(row.f).
      case IDENT -> factory.newSelect(factory.newIdentifier(RECORD), argument.ident().name(), true);
      case SELECT -> factory.newSelect(argument.select().operand(), argument.select().field(), true);
      default -> factory.reportError("has() takes a field, as in has(expire_time) or has(address.city)");
    });
  });

  private static final CelOptions OPTIONS = CelOptions.current().maxExpressionCodePointSize(Filter.MAX_LENGTH).build();

  /** The operation of each CEL operator and function that an expression supports, by the name of the function. */
  private final Map<String, ExpressionOperation> operations = new HashMap<>();

  public CelExpressionCompiler() {
    operations.put(Operator.LOGICAL_AND.getFunction(), new ExpressionLogic(Expression.Operator.AND));
    operations.put(Operator.LOGICAL_OR.getFunction(), new ExpressionLogic(Expression.Operator.OR));
    operations.put(Operator.LOGICAL_NOT.getFunction(), new ExpressionNegation());
    operations.put(Operator.EQUALS.getFunction(), new ExpressionComparison(Expression.Operator.EQUALS));
    operations.put(Operator.NOT_EQUALS.getFunction(), new ExpressionComparison(Expression.Operator.NOT_EQUALS));
    operations.put(Operator.LESS.getFunction(), new ExpressionComparison(Expression.Operator.LESS));
    operations.put(Operator.LESS_EQUALS.getFunction(), new ExpressionComparison(Expression.Operator.LESS_EQUALS));
    operations.put(Operator.GREATER.getFunction(), new ExpressionComparison(Expression.Operator.GREATER));
    operations.put(Operator.GREATER_EQUALS.getFunction(), new ExpressionComparison(Expression.Operator.GREATER_EQUALS));
    operations.put(Operator.ADD.getFunction(), new ExpressionArithmetic(Expression.Operator.ADD, "+"));
    operations.put(Operator.SUBTRACT.getFunction(), new ExpressionArithmetic(Expression.Operator.SUBTRACT, "-"));
    operations.put(Operator.MULTIPLY.getFunction(), new ExpressionArithmetic(Expression.Operator.MULTIPLY, "*"));
    operations.put(Operator.DIVIDE.getFunction(), new ExpressionArithmetic(Expression.Operator.DIVIDE, "/"));
    operations.put(Operator.MODULO.getFunction(), new ExpressionArithmetic(Expression.Operator.MODULO, "%"));
    operations.put(Operator.NEGATE.getFunction(), new ExpressionNegative());
    operations.put(Operator.IN.getFunction(), new ExpressionMembership());
    operations.put(Operator.OLD_IN.getFunction(), new ExpressionMembership());
    operations.put(Operator.INDEX.getFunction(), new ExpressionIndexing());
    operations.put("startsWith", new ExpressionStringMatch(Expression.StringMatch.Kind.STARTS_WITH));
    operations.put("endsWith", new ExpressionStringMatch(Expression.StringMatch.Kind.ENDS_WITH));
    operations.put("contains", new ExpressionStringMatch(Expression.StringMatch.Kind.CONTAINS));
    operations.put("timestamp", new ExpressionConversion(Type.Scalar.TIMESTAMP));
    operations.put("duration", new ExpressionConversion(Type.Scalar.DURATION));
    operations.put(Operator.CONDITIONAL.getFunction(),
        new RefusedOperation("the conditional operator '? :' is not supported in an expression"));
    operations.put("matches", new RefusedOperation(
        "matches() is not supported, because the databases have no common regular expressions"));
  }

  /**
   * Compiles an expression.
   *
   * @param text the text of the expression, for example {@code state == 'OPEN' && total_cents > params.min}
   * @param scope the fields that the expression can read
   * @param parameters the types of the values that the expression can read as {@code params.name}, by name;
   * {@link ParameterValues#types()} gives them from the values
   * @param result the type that the value of the expression must have, for example {@link Type.Scalar#BOOL} for a
   * condition
   * @param functions the functions besides those of CEL that the expression can use, such as {@code now()}
   * @return the expression
   * @throws InvalidExpressionException if the expression does not compile
   */
  public CompiledExpression compile(String text, Scope scope, Map<String, Type> parameters, Type result,
      Set<Function> functions) {
    CelTypes types = new CelTypes();
    StructType row = types.row(scope);
    StructType paramStruct = types.struct(PARAMS_TYPE,
        parameters.entrySet().stream().map(entry -> new Type.Member(entry.getKey(), entry.getValue(), false)).toList());
    CelCompilerBuilder builder = CelCompilerFactory.standardCelCompilerBuilder()
        .setOptions(OPTIONS)
        .setStandardMacros(CelStandardMacro.EXISTS, CelStandardMacro.ALL)
        .addMacros(HAS)
        .setTypeProvider(types)
        .addVar(RECORD, row)
        .addVar(PARAMS, paramStruct);
    for (Function function : functions) {
      builder.addFunctionDeclarations(CelFunctionDecl.newFunctionDeclaration(function.name(),
          CelOverloadDecl.newGlobalOverload("aquery_" + function.name(), types.cel(function.result()),
              function.parameters().stream().map(types::cel).toArray(CelType[]::new))));
    }
    for (Scope.Field field : scope.fields()) {
      builder.addVar(field.name(), types.cel(field.type()));
    }
    for (Scope.Edge edge : scope.edges()) {
      builder.addVar(edge.name(), types.edge(edge));
    }
    CelCompiler compiler = builder.build();
    CelValidationResult checked = compiler.compile(text);
    if (checked.hasError()) {
      CelIssue issue = checked.getErrors().getFirst();
      throw new InvalidExpressionException(new CelSourceText(checked.getSource()).offsetOf(issue.getSourceLocation()),
          "invalid expression: %s", issue.getMessage().replace(" (in container '')", ""));
    }
    CelAbstractSyntaxTree tree;
    try {
      tree = checked.getAst();
    } catch (CelValidationException unexpected) {
      throw new IllegalStateException("cel-java gave no tree for an expression without errors", unexpected);
    }
    CompiledExpression expression = new ExpressionTranslation(new CelTree(tree, types), scope, parameters, functions,
        operations).translate(text);
    if (!expression.type().equals(result)) {
      throw new InvalidExpressionException(expression.root().position(),
          "expected an expression of type %s but it is of type %s", types.describe(result),
          types.describe(expression.type()));
    }
    return expression;
  }
}
