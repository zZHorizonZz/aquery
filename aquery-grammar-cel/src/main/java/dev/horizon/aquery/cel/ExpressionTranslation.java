package dev.horizon.aquery.cel;

import dev.horizon.aquery.cel.Expression.FieldReference;
import dev.horizon.aquery.cel.Expression.FunctionCall;
import dev.horizon.aquery.cel.Expression.IndexAccess;
import dev.horizon.aquery.cel.Expression.ListLiteral;
import dev.horizon.aquery.cel.Expression.Literal;
import dev.horizon.aquery.cel.Expression.MemberAccess;
import dev.horizon.aquery.cel.Expression.Parameter;
import dev.horizon.aquery.cel.Expression.Presence;
import dev.horizon.aquery.cel.Expression.Quantifier;
import dev.horizon.aquery.cel.Expression.Related;
import dev.horizon.aquery.cel.Expression.Row;
import dev.horizon.aquery.cel.Expression.Variable;
import dev.horizon.aquery.shaded.cel.common.Operator;
import dev.horizon.aquery.shaded.cel.common.ast.CelConstant;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr;
import dev.horizon.aquery.shaded.cel.common.ast.CelExpr.CelComprehension;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * The translation of one checked CEL expression into an {@link Expression} tree.
 *
 * <p>
 * The type checker of cel-java already checked the names and the types; the translation reads the type of each node
 * from the tree. It reads the names, the fields, the members, the related rows of edges and {@code exists()} and
 * {@code all()} itself, and gives each call to the operation of its function. A form of CEL that SQL cannot answer
 * is a {@link Refusal}, which {@link #translate} turns into an {@link InvalidExpressionException} with the offset of
 * the node.
 *
 * <p>
 * A call of a function that the caller declared, as {@code now()}, is a {@link FunctionCall} of that function.
 */
final class ExpressionTranslation {

  private final CelTree tree;
  private final Scope scope;
  private final Map<String, Type> parameters;
  private final Set<Function> functions;
  private final Map<String, ExpressionOperation> operations;
  /** The variables of exists() and all() that are in scope: a {@link Variable} or a {@link Row}. */
  private final Map<String, Deque<Object>> variables = new HashMap<>();

  ExpressionTranslation(CelTree tree, Scope scope, Map<String, Type> parameters, Set<Function> functions,
      Map<String, ExpressionOperation> operations) {
    this.tree = tree;
    this.scope = scope;
    this.parameters = parameters;
    this.functions = functions;
    this.operations = operations;
  }

  /** Gives the compiled expression of a text. */
  CompiledExpression translate(String text) {
    try {
      return new CompiledExpression(text, expression(tree.root()), parameters);
    } catch (Refusal refusal) {
      throw new InvalidExpressionException(tree.position(refusal), "%s", refusal.getMessage());
    }
  }

  /** Gives the tree that the translation reads. */
  CelTree tree() {
    return tree;
  }

  /** Gives the type that the checker found for a node. */
  Type type(CelExpr node) {
    return tree.type(node);
  }

  /** Gives the offset of a node in the text. */
  int position(CelExpr node) {
    return tree.position(node);
  }

  /**
   * Gives the expression of a node.
   *
   * @throws Refusal if the node is not supported
   */
  Expression expression(CelExpr node) {
    return switch (node.getKind()) {
      case CONSTANT -> constant(node);
      case IDENT -> name(node);
      case SELECT -> select(node);
      case CALL -> call(node);
      case LIST -> list(node);
      case COMPREHENSION -> comprehension(node);
      case MAP -> throw tree.refuse(node, "a map literal is not supported in an expression");
      case STRUCT -> throw tree.refuse(node, "a message literal is not supported in an expression");
      default -> throw tree.refuse(node, "the expression is empty");
    };
  }

  private Expression call(CelExpr node) {
    String function = node.call().function();
    ExpressionOperation operation = operations.get(function);
    if (operation != null) {
      return operation.translate(node, this);
    }
    for (Function declared : functions) {
      // The checker of cel-java checked the arguments against the parameters of the function.
      if (declared.name().equals(function)) {
        List<Expression> arguments = new ArrayList<>();
        for (CelExpr argument : node.call().args()) {
          arguments.add(expression(argument));
        }
        return new FunctionCall(declared, arguments, position(node));
      }
    }
    throw tree.isOperator(function)
        ? tree.refuse(node, "the operator '%s' is not supported in an expression", function)
        : tree.refuse(node, "%s() is not supported in an expression", function);
  }

  private Expression constant(CelExpr node) {
    CelConstant constant = node.constant();
    int position = position(node);
    return switch (constant.getKind()) {
      case BOOLEAN_VALUE -> new Literal(Type.Scalar.BOOL, constant.booleanValue(), position);
      case INT64_VALUE -> new Literal(Type.Scalar.INT, constant.int64Value(), position);
      case DOUBLE_VALUE -> {
        if (!Double.isFinite(constant.doubleValue())) {
          throw tree.refuse(node, "a double must be finite");
        }
        yield new Literal(Type.Scalar.DOUBLE, constant.doubleValue(), position);
      }
      case STRING_VALUE -> new Literal(Type.Scalar.STRING, constant.stringValue(), position);
      case BYTES_VALUE -> new Literal(Type.Scalar.BYTES, constant.bytesValue().toByteArray(), position);
      case UINT64_VALUE -> throw tree.refuse(node, "unsigned integers are not supported; write the number without 'u'");
      case NULL_VALUE -> throw tree.refuse(node, "null is not supported; use has() to test if a field is set");
      default -> throw tree.refuse(node, "the constant is not supported");
    };
  }

  private Expression name(CelExpr node) {
    String name = node.ident().name();
    Object bound = bound(name);
    if (bound instanceof Variable variable) {
      return new Variable(variable.name(), variable.type(), position(node));
    }
    if (bound instanceof Row) {
      throw tree.refuse(node, "the row '%s' is not a value; read its fields, as in %s.name", name, name);
    }
    Optional<Scope.Field> field = scope.field(name);
    if (field.isPresent()) {
      return new FieldReference(field.get(), position(node));
    }
    Optional<Scope.Edge> edge = scope.edge(name);
    if (edge.isPresent()) {
      throw notAValue(node, edge.get());
    }
    if (name.equals(CelExpressionCompiler.PARAMS)) {
      throw tree.refuse(node, "params is not a value; read one parameter, as in params.min");
    }
    throw tree.refuse(node, "'%s' is not a field", name);
  }

  private Expression select(CelExpr node) {
    CelExpr.CelSelect select = node.select();
    CelExpr operand = select.operand();
    String name = select.field();
    if (operand.getKind() == CelExpr.ExprKind.Kind.IDENT && bound(operand.ident().name()) == null) {
      String root = operand.ident().name();
      if (root.equals(CelExpressionCompiler.PARAMS)) {
        if (select.testOnly()) {
          throw tree.refuse(node, "has() takes a field, not a parameter");
        }
        return new Parameter(name, parameters.get(name), position(node));
      }
      if (root.equals(CelExpressionCompiler.RECORD)) {
        Optional<Scope.Edge> edge = scope.edge(name);
        if (edge.isPresent()) {
          // has(customer): the row has a related row.
          return exists(new Row(name, edge.get(), null), node);
        }
        Scope.Field field = scope.field(name).orElseThrow(() -> tree.refuse(node, "'%s' is not a field", name));
        FieldReference reference = new FieldReference(field, position(node));
        if (!select.testOnly()) {
          return reference;
        }
        if (!field.nullable()) {
          throw tree.refuse(node, "the field '%s' always has a value; has() tests only a field that can be unset",
              name);
        }
        return new Presence(reference, position(node));
      }
    }
    Optional<Path> path = path(operand);
    if (path.isPresent()) {
      // A member of the related row of an edge to one row, as in customer.name.
      Path along = path.get();
      if (along.edge().many()) {
        throw notAValue(operand, along.edge());
      }
      Row row = new Row(along.name(), along.edge(), along.from());
      return along.wrap(new Related(Related.Kind.VALUE, row, rowMember(row, node, name, select.testOnly()),
          position(operand)));
    }
    if (operand.getKind() == CelExpr.ExprKind.Kind.IDENT && bound(operand.ident().name()) instanceof Row row) {
      return rowMember(row, node, name, select.testOnly());
    }
    return inside(expression(operand), parent -> member(node, parent, name, select.testOnly()));
  }

  /** Gives a member of a struct, or a key of a map, as in address.country or labels.tier. */
  private Expression member(CelExpr node, Expression parent, String name, boolean testOnly) {
    Expression member = switch (parent.type()) {
      case Type.Struct struct -> {
        Type.Member found = struct.member(name)
            .orElseThrow(() -> tree.refuse(node, "'%s' is not a member of %s", name, struct.name()));
        if (testOnly && !found.optional()) {
          throw tree.refuse(node, "the member '%s' always has a value; has() tests only a member that can be unset",
              name);
        }
        yield new MemberAccess(parent, found, position(node));
      }
      case Type.MapOf map -> new IndexAccess(parent, new Literal(Type.Scalar.STRING, name, position(node)),
          map.value(), position(node));
      default -> throw tree.refuse(node, "'%s' is not a member of a %s", name, tree.describe(parent.type()));
    };
    return testOnly ? new Presence(member, position(node)) : member;
  }

  /** Gives a field of a related row, or tells if it has a value, or if the row has a row along its edge. */
  private Expression rowMember(Row row, CelExpr node, String name, boolean testOnly) {
    Scope target = row.edge().target();
    Optional<Scope.Field> field = target.field(name);
    if (field.isEmpty()) {
      Scope.Edge edge = target.edge(name)
          .orElseThrow(() -> tree.refuse(node, "'%s' is not a field of %s", name, target.name()));
      if (!testOnly) {
        throw notAValue(node, edge);
      }
      return exists(new Row(row.name() + "." + name, edge, row), node);
    }
    FieldReference reference = new FieldReference(field.get(), row, position(node));
    if (!testOnly) {
      return reference;
    }
    if (!field.get().nullable()) {
      throw tree.refuse(node, "the field '%s' always has a value; has() tests only a field that can be unset", name);
    }
    return new Presence(reference, position(node));
  }

  /** Gives the condition that a row has one or more rows along an edge. */
  private Expression exists(Row row, CelExpr node) {
    return new Related(Related.Kind.EXISTS, row, new Literal(Type.Scalar.BOOL, true, position(node)),
        position(node));
  }

  /**
   * Edges followed from a row: the related rows of each edge to one row on the way, and the last edge.
   *
   * @param via the related rows of the edges to one row before the last edge, from the first
   * @param from the row that the last edge starts from, or null for the row of the expression
   * @param edge the last edge
   * @param name the path in the text, as in customer.orders
   */
  private record Path(List<Row> via, Row from, Scope.Edge edge, String name) {

    /** Reads an expression over the row at the end of the path from the row the path starts at. */
    Expression wrap(Expression inner) {
      Expression wrapped = inner;
      for (int i = via.size() - 1; i >= 0; i--) {
        wrapped = new Related(Related.Kind.VALUE, via.get(i), wrapped, wrapped.position());
      }
      return wrapped;
    }
  }

  /** Finds the edges that an expression follows, as in items, customer.orders or i.product; empty if it is no edge. */
  private Optional<Path> path(CelExpr node) {
    if (node.getKind() == CelExpr.ExprKind.Kind.IDENT) {
      String name = node.ident().name();
      return bound(name) != null ? Optional.empty()
          : scope.edge(name).map(edge -> new Path(List.of(), null, edge, name));
    }
    if (node.getKind() != CelExpr.ExprKind.Kind.SELECT || node.select().testOnly()) {
      return Optional.empty();
    }
    CelExpr operand = node.select().operand();
    String name = node.select().field();
    if (operand.getKind() == CelExpr.ExprKind.Kind.IDENT && bound(operand.ident().name()) instanceof Row row) {
      return row.edge().target().edge(name).map(edge -> new Path(List.of(), row, edge, row.name() + "." + name));
    }
    Optional<Path> outer = path(operand);
    if (outer.isEmpty() || outer.get().edge().many()) {
      return Optional.empty();
    }
    Path before = outer.get();
    Row row = new Row(before.name(), before.edge(), before.from());
    List<Row> via = new ArrayList<>(before.via());
    via.add(row);
    return row.edge().target().edge(name).map(edge -> new Path(List.copyOf(via), row, edge, before.name() + "." + name));
  }

  /**
   * Applies a step to the value of an expression. A value of a related row stays inside its {@link Related}, so that
   * customer.address.country reads the country in the related row.
   */
  Expression inside(Expression parent, UnaryOperator<Expression> step) {
    if (parent instanceof Related related && related.kind() == Related.Kind.VALUE) {
      return new Related(Related.Kind.VALUE, related.row(), inside(related.body(), step), related.position());
    }
    return step.apply(parent);
  }

  private Refusal notAValue(CelExpr node, Scope.Edge edge) {
    String name = edge.name();
    return edge.many()
        ? tree.refuse(node, "the edge '%s' has many rows; use %s.exists(x, ...) or %s.all(x, ...)", name, name, name)
        : tree.refuse(node, "the edge '%s' is not a value; read a field of it, as in %s.name, or test it with has(%s)",
            name, name, name);
  }

  private Expression list(CelExpr node) {
    List<Expression> elements = new ArrayList<>();
    for (CelExpr element : node.list().elements()) {
      elements.add(expression(element));
    }
    return new ListLiteral(elements, type(node), position(node));
  }

  private Expression comprehension(CelExpr node) {
    CelComprehension comprehension = node.comprehension();
    Quantifier.Kind kind = quantifier(comprehension);
    if (kind == null) {
      throw tree.refuse(node, "only the macros exists() and all() are supported");
    }
    CelExpr step = comprehension.loopStep();
    CelExpr predicate = step.call().args().get(0).getKind() == CelExpr.ExprKind.Kind.IDENT
        && step.call().args().get(0).ident().name().equals(comprehension.accuVar()) ? step.call().args().get(1)
            : step.call().args().get(0);
    Optional<Path> path = path(comprehension.iterRange());
    if (path.isPresent()) {
      // exists() or all() over the related rows of an edge, as in items.exists(i, i.quantity > 1).
      Path along = path.get();
      if (!along.edge().many()) {
        throw tree.refuse(comprehension.iterRange(), "%s() goes over a list or an edge to many rows; '%s' has one row",
            kind.name().toLowerCase(), along.name());
      }
      Row row = new Row(comprehension.iterVar(), along.edge(), along.from());
      Expression condition = withVariable(row.name(), row, () -> expression(predicate));
      Related.Kind related = kind == Quantifier.Kind.EXISTS ? Related.Kind.EXISTS : Related.Kind.ALL;
      return along.wrap(new Related(related, row, condition, position(node)));
    }
    return inside(expression(comprehension.iterRange()), range -> {
      if (!isJson(range) || !(range.type() instanceof Type.ListOf list)) {
        throw tree.refuse(comprehension.iterRange(), "%s() is supported only over a list field or an edge to many rows",
            kind.name().toLowerCase());
      }
      Variable variable = new Variable(comprehension.iterVar(), list.element(), position(node));
      Expression condition = withVariable(variable.name(), variable, () -> expression(predicate));
      return new Quantifier(kind, variable, range, condition, position(node));
    });
  }

  /** Translates an expression in which a name is a variable of exists() or all(). */
  private Expression withVariable(String name, Object variable, Supplier<Expression> translate) {
    variables.computeIfAbsent(name, ignored -> new ArrayDeque<>()).push(variable);
    try {
      return translate.get();
    } finally {
      variables.get(name).pop();
    }
  }

  private Quantifier.Kind quantifier(CelComprehension comprehension) {
    CelExpr init = comprehension.accuInit();
    CelExpr step = comprehension.loopStep();
    if (!comprehension.iterVar2().isEmpty() || init.getKind() != CelExpr.ExprKind.Kind.CONSTANT
        || init.constant().getKind() != CelConstant.Kind.BOOLEAN_VALUE || step.getKind() != CelExpr.ExprKind.Kind.CALL
        || step.call().args().size() != 2) {
      return null;
    }
    boolean initial = init.constant().booleanValue();
    String function = step.call().function();
    if (!initial && function.equals(Operator.LOGICAL_OR.getFunction())) {
      return Quantifier.Kind.EXISTS;
    }
    if (initial && function.equals(Operator.LOGICAL_AND.getFunction())) {
      return Quantifier.Kind.ALL;
    }
    return null;
  }

  /** Gives the innermost variable with a name: a {@link Variable} or a {@link Row}; null for none. */
  private Object bound(String name) {
    Deque<Object> bound = variables.get(name);
    return bound == null || bound.isEmpty() ? null : bound.peek();
  }

  /** Tells if an expression is stored as JSON: a list, a map or a struct. */
  boolean isJson(Expression expression) {
    return switch (expression) {
      case FieldReference field -> field.field().storage() == Scope.Storage.JSON;
      case Variable variable -> !(variable.type() instanceof Type.Scalar);
      case MemberAccess member -> !(member.type() instanceof Type.Scalar);
      case IndexAccess index -> !(index.type() instanceof Type.Scalar);
      default -> false;
    };
  }

  /** Tells if an expression is a constant or a parameter that is not a list. */
  boolean isConstant(Expression expression) {
    return expression instanceof Literal
        || (expression instanceof Parameter parameter && parameter.type() instanceof Type.Scalar);
  }
}
