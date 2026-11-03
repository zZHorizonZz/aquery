package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.aip160.Filter;
import dev.horizon.aquery.cel.Scope;
import dev.horizon.aquery.cel.Type;
import dev.horizon.aquery.cel.internal.CelSourceText;
import dev.horizon.aquery.cel.internal.CelTree;
import dev.horizon.aquery.cel.internal.CelTypes;
import dev.horizon.aquery.cel.internal.MacroDeclaration;
import dev.horizon.aquery.shaded.cel.common.CelFunctionDecl;
import dev.horizon.aquery.shaded.cel.common.CelIssue;
import dev.horizon.aquery.shaded.cel.common.CelOptions;
import dev.horizon.aquery.shaded.cel.common.CelOverloadDecl;
import dev.horizon.aquery.shaded.cel.common.CelValidationException;
import dev.horizon.aquery.shaded.cel.common.CelValidationResult;
import dev.horizon.aquery.shaded.cel.common.types.CelType;
import dev.horizon.aquery.shaded.cel.compiler.CelCompiler;
import dev.horizon.aquery.shaded.cel.compiler.CelCompilerBuilder;
import dev.horizon.aquery.shaded.cel.compiler.CelCompilerFactory;
import dev.horizon.aquery.shaded.cel.parser.CelStandardMacro;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class Checker {

  static final String PARAMS = "params";

  private static final CelOptions OPTIONS = CelOptions.current().maxExpressionCodePointSize(Filter.MAX_LENGTH).build();

  private final CelTypes types = new CelTypes();
  private final Map<String, Overload> overloads = new HashMap<>();
  private final CelCompiler compiler;

  Checker(CelGrammar grammar, CelEnvironment environment) {
    CelCompilerBuilder builder = CelCompilerFactory.standardCelCompilerBuilder()
        .setOptions(OPTIONS)
        .setStandardMacros(CelStandardMacro.EXISTS, CelStandardMacro.ALL)
        .setTypeProvider(types);
    grammar.macros().forEach(macro -> builder.addMacros(new MacroDeclaration(macro).cel()));
    declare(builder, environment.scope());
    environment.rows().forEach((name, row) -> builder.addVar(name, types.row(row)));
    declare(builder, environment.parameters());
    declare(builder, grammar.operations());
    compiler = builder.build();
  }

  CelTree check(String text) {
    CelValidationResult checked = compiler.compile(text);
    if (checked.hasError()) {
      CelIssue issue = checked.getErrors().getFirst();
      throw new InvalidExpressionException(new CelSourceText(checked.getSource()).offsetOf(issue.getSourceLocation()),
          "invalid expression: %s", issue.getMessage().replace(" (in container '')", ""));
    }
    try {
      return new CelTree(checked.getAst(), types);
    } catch (CelValidationException unexpected) {
      throw new IllegalStateException("cel-java gave no tree for an expression without errors", unexpected);
    }
  }

  Optional<Overload> overload(Site call) {
    List<String> ids = call.tree().overloads(call.node());
    return ids.size() == 1 ? Optional.ofNullable(overloads.get(ids.getFirst())) : Optional.empty();
  }

  private void declare(CelCompilerBuilder builder, Scope scope) {
    builder.addVar(CelTree.RECORD, types.row(scope));
    scope.fields().forEach(field -> builder.addVar(field.name(), types.cel(field.type())));
    scope.edges().forEach(edge -> builder.addVar(edge.name(), types.edge(edge)));
  }

  private void declare(CelCompilerBuilder builder, Parameters parameters) {
    List<Type.Member> members = parameters.types().entrySet().stream()
        .map(entry -> new Type.Member(entry.getKey(), entry.getValue(), false)).toList();
    builder.addVar(PARAMS, types.struct("aquery.Params", members));
  }

  private void declare(CelCompilerBuilder builder, List<Operation> operations) {
    Map<String, List<CelOverloadDecl>> functions = new LinkedHashMap<>();
    for (Operation operation : operations) {
      for (Overload overload : operation.overloads()) {
        String id = "aquery_" + overloads.size();
        overloads.put(id, overload);
        functions.computeIfAbsent(operation.function(), _ -> new ArrayList<>()).add(declaration(id, overload));
      }
    }
    functions.forEach((name, declarations) -> builder.addFunctionDeclarations(
        CelFunctionDecl.newFunctionDeclaration(name, declarations)));
  }

  private CelOverloadDecl declaration(String id, Overload overload) {
    CelType result = types.cel(overload.result());
    CelType[] operands = overload.operands().stream().map(types::cel).toArray(CelType[]::new);
    return switch (overload) {
      case Overload.Global _ -> CelOverloadDecl.newGlobalOverload(id, result, operands);
      case Overload.Member _ -> CelOverloadDecl.newMemberOverload(id, result, operands);
    };
  }

}
