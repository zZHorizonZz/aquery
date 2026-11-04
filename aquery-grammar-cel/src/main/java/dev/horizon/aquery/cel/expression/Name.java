package dev.horizon.aquery.cel.expression;

import dev.horizon.aquery.cel.Scope;
import dev.horizon.aquery.cel.Type;

import dev.horizon.aquery.cel.expression.Expression.FieldReference;
import dev.horizon.aquery.cel.expression.Expression.IndexAccess;
import dev.horizon.aquery.cel.expression.Expression.Literal;
import dev.horizon.aquery.cel.expression.Expression.MemberAccess;
import dev.horizon.aquery.cel.expression.Expression.Parameter;
import dev.horizon.aquery.cel.expression.Expression.Presence;
import dev.horizon.aquery.cel.expression.Expression.Related;
import dev.horizon.aquery.cel.expression.Expression.Row;
import dev.horizon.aquery.cel.expression.Expression.Variable;
import java.util.ArrayList;
import java.util.List;

sealed interface Name permits Name.RowName, Name.NamedRowName, Name.EdgeName, Name.ParameterName, Name.ValueName {

  Expression value(Site site);

  Name member(String field, Site site);

  Expression presence(String field, Site site);

  default Expression quantify(Quantification quantification, Translation translation) {
    throw quantification.site().refuse("%s() is supported only over a list field or an edge to many rows",
        quantification.function());
  }

  default InvalidExpressionException unknown(String field, Site site) {
    return site.refuse("'%s' is not a field", field);
  }

  default InvalidExpressionException unknown(String field, Scope scope, Site site) {
    return site.refuse("'%s' is not a field of %s", field, scope.name());
  }

  default Name decorate(Site site) {
    return this;
  }

  default Expression presence(Expression value, Site site) {
    if (!value.optional()) {
      throw site.refuse("%s always has a value; has() tests only a field that can be unset or a member that is"
          + " optional", value.describe());
    }
    return new Presence(site.decorate(value), site.position());
  }

  record RowName(Scope scope, Row row, String path) implements Name {

    @Override
    public Expression value(Site site) {
      throw site.refuse("the row '%s' is not a value; read its fields, as in %s.name", path, path);
    }

    @Override
    public Name member(String field, Site site) {
      return scope.field(field).<Name> map(found -> new ValueName(new FieldReference(found, row, site.position())))
          .orElseGet(() -> new EdgeName(scope.edge(field).orElseThrow(() -> unknown(field, site)), row, List.of(),
              prefix() + field));
    }

    @Override
    public Expression presence(String field, Site site) {
      return scope.field(field).map(found -> presence(new FieldReference(found, row, site.position()), site))
          .orElseGet(() -> new Related.Exists(new Row.Related(prefix() + field, scope.edge(field)
              .orElseThrow(() -> unknown(field, site)), row),
              new Literal(Type.Scalar.BOOL, true, site.position()), site.position()));
    }

    private String prefix() {
      return path.isEmpty() ? "" : path + ".";
    }
  }

  record NamedRowName(Row.Named row, Scope scope) implements Name {

    @Override
    public Expression value(Site site) {
      throw site.refuse("the row '%s' is not a value; read one of its fields", row.name());
    }

    @Override
    public Name member(String field, Site site) {
      return new ValueName(new FieldReference(field(field, site), row, site.position()));
    }

    @Override
    public Expression presence(String field, Site site) {
      return presence(new FieldReference(field(field, site), row, site.position()), site);
    }

    private Scope.Field field(String field, Site site) {
      return scope.field(field).orElseThrow(() -> site.refuse("'%s' is an edge; read only the fields of the row '%s'",
          field, row.name()));
    }
  }

  record EdgeName(Scope.Edge edge, Row from, List<Row.Related> via, String path) implements Name {

    @Override
    public Expression value(Site site) {
      throw refusal(site);
    }

    @Override
    public Name member(String field, Site site) {
      Row.Related row = row(site);
      Scope target = edge.target();
      return target.field(field)
          .<Name> map(found -> new ValueName(wrap(new Related.Value(row, site.decorate(new FieldReference(found, row,
              site.position())), site.position()), site)))
          .orElseGet(() -> new EdgeName(target.edge(field).orElseThrow(() -> unknown(field, target, site)), row,
              append(row), path + "." + field));
    }

    @Override
    public Expression presence(String field, Site site) {
      Row.Related row = row(site);
      Scope target = edge.target();
      Expression body = target.field(field).map(found -> presence(new FieldReference(found, row, site.position()),
          site)).orElseGet(() -> new Related.Exists(
              new Row.Related(path + "." + field, target.edge(field)
                  .orElseThrow(() -> unknown(field, target, site)), row),
              new Literal(Type.Scalar.BOOL, true, site.position()), site.position()));
      return wrap(new Related.Value(row, site.decorate(body), site.position()), site);
    }

    @Override
    public Expression quantify(Quantification quantification, Translation translation) {
      if (!edge.many()) {
        throw quantification.site().refuse("%s() goes over a list or an edge to many rows; '%s' has one row",
            quantification.function(), path);
      }
      Row.Related row = new Row.Related(quantification.variable(), edge, from);
      Expression predicate = translation.within(quantification.variable(),
          new RowName(edge.target(), row, quantification.variable()), quantification.predicate());
      return wrap(quantification.related(row, predicate), quantification.site());
    }

    private Row.Related row(Site site) {
      if (edge.many()) {
        throw refusal(site);
      }
      return new Row.Related(path, edge, from);
    }

    private InvalidExpressionException refusal(Site site) {
      String name = edge.name();
      return edge.many()
          ? site.refuse("the edge '%s' has many rows; use %s.exists(x, ...) or %s.all(x, ...)", name, name, name)
          : site.refuse("the edge '%s' is not a value; read a field of it, as in %s.name, or test it with has(%s)",
              name, name, name);
    }

    private List<Row.Related> append(Row.Related row) {
      List<Row.Related> all = new ArrayList<>(via);
      all.add(row);
      return all;
    }

    private Expression wrap(Expression inner, Site site) {
      Expression body = inner;
      for (Row.Related row : via.reversed()) {
        body = new Related.Value(row, site.decorate(body), body.position());
      }
      return body;
    }
  }

  record ParameterName(Parameters parameters) implements Name {

    @Override
    public Expression value(Site site) {
      throw site.refuse("params is not a value; read one parameter, as in params.min");
    }

    @Override
    public Name member(String field, Site site) {
      return new ValueName(new Parameter(field, parameters.type(field)
          .orElseThrow(() -> site.refuse("'%s' is not a parameter", field)), site.position()));
    }

    @Override
    public Expression presence(String field, Site site) {
      throw site.refuse("has() takes a field, not a parameter");
    }
  }

  record ValueName(Expression value) implements Name {

    @Override
    public Expression value(Site site) {
      return value;
    }

    @Override
    public Name decorate(Site site) {
      return new ValueName(site.decorate(value));
    }

    @Override
    public Name member(String field, Site site) {
      return new ValueName(value.inside(parent -> member(parent, field, site)));
    }

    @Override
    public Expression presence(String field, Site site) {
      return value.inside(parent -> presence(member(parent, field, site), site));
    }

    @Override
    public Expression quantify(Quantification quantification, Translation translation) {
      return value.inside(range -> {
        if (!range.json() || !(range.type() instanceof Type.ListOf list)) {
          return Name.super.quantify(quantification, translation);
        }
        Variable variable = new Variable(quantification.variable(), list.element(), quantification.site().position());
        return quantification.quantifier(variable, range, translation.within(quantification.variable(),
            new ValueName(variable), quantification.predicate()));
      });
    }

    private Expression member(Expression parent, String field, Site site) {
      return switch (parent.type()) {
        case Type.Struct struct -> new MemberAccess(parent, struct.member(field)
            .orElseThrow(() -> site.refuse("'%s' is not a member of a %s", field, struct.describe())), site.position());
        case Type.MapOf map -> new IndexAccess(parent, new Literal(Type.Scalar.STRING, field, site.position()),
            map.value(), site.position());
        default -> throw site.refuse("'%s' is not a member of a %s", field, parent.type().describe());
      };
    }
  }
}
