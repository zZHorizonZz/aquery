package dev.horizon.aquery.cel;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dev.horizon.aquery.shaded.cel.common.types.CelType;
import dev.horizon.aquery.shaded.cel.common.types.CelTypeProvider;
import dev.horizon.aquery.shaded.cel.common.types.ListType;
import dev.horizon.aquery.shaded.cel.common.types.MapType;
import dev.horizon.aquery.shaded.cel.common.types.SimpleType;
import dev.horizon.aquery.shaded.cel.common.types.StructType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The types of one compilation, as cel-java and as aquery see them. The type checker of cel-java finds the structs
 * in it.
 */
final class CelTypes implements CelTypeProvider {

  private final Map<String, StructType> structs = new LinkedHashMap<>();
  private final Map<String, Type.Struct> sources = new LinkedHashMap<>();

  /** Gives the type of a struct with members. */
  StructType struct(String name, List<Type.Member> members) {
    return (StructType) cel(new Type.Struct(name, members));
  }

  /**
   * Gives the type of a row of a scope: its fields and its edges. The members are found when the checker needs them,
   * thus scopes whose edges go to each other give types that go to each other.
   */
  StructType row(Scope scope) {
    StructType known = structs.get(scope.name());
    if (known != null) {
      return known;
    }
    ImmutableSet<String> names = Stream.concat(scope.fields().stream().map(Scope.Field::name),
        scope.edges().stream().map(Scope.Edge::name)).collect(ImmutableSet.toImmutableSet());
    StructType created = StructType.create(scope.name(), names,
        member -> scope.field(member).map(field -> cel(field.type())).or(() -> scope.edge(member).map(this::edge)));
    structs.put(scope.name(), created);
    return created;
  }

  /** Gives the type of an edge: the row of its table, or a list of them. */
  CelType edge(Scope.Edge edge) {
    StructType target = row(edge.target());
    return edge.many() ? ListType.create(target) : target;
  }

  /** Gives the type of cel-java for a type of aquery. */
  CelType cel(Type type) {
    return switch (type) {
      case Type.Scalar scalar -> switch (scalar) {
        case BOOL -> SimpleType.BOOL;
        case INT -> SimpleType.INT;
        case DOUBLE -> SimpleType.DOUBLE;
        case STRING -> SimpleType.STRING;
        case BYTES -> SimpleType.BYTES;
        case TIMESTAMP -> SimpleType.TIMESTAMP;
        case DURATION -> SimpleType.DURATION;
      };
      case Type.ListOf list -> ListType.create(cel(list.element()));
      case Type.MapOf map -> MapType.create(cel(map.key()), cel(map.value()));
      case Type.Any ignored -> SimpleType.DYN;
      case Type.Struct struct -> {
        StructType known = structs.get(struct.name());
        if (known != null) {
          yield known;
        }
        ImmutableSet<String> names = struct.members().stream().map(Type.Member::name)
            .collect(ImmutableSet.toImmutableSet());
        StructType created = StructType.create(struct.name(), names,
            member -> struct.member(member).map(found -> cel(found.type())));
        structs.put(struct.name(), created);
        sources.put(struct.name(), struct);
        yield created;
      }
    };
  }

  /** Gives the type of aquery for a type of cel-java, or {@link Type#ANY} for a type that aquery does not have. */
  Type aquery(CelType type) {
    if (type.equals(SimpleType.BOOL)) {
      return Type.Scalar.BOOL;
    } else if (type.equals(SimpleType.INT)) {
      return Type.Scalar.INT;
    } else if (type.equals(SimpleType.DOUBLE)) {
      return Type.Scalar.DOUBLE;
    } else if (type.equals(SimpleType.STRING)) {
      return Type.Scalar.STRING;
    } else if (type.equals(SimpleType.BYTES)) {
      return Type.Scalar.BYTES;
    } else if (type.equals(SimpleType.TIMESTAMP)) {
      return Type.Scalar.TIMESTAMP;
    } else if (type.equals(SimpleType.DURATION)) {
      return Type.Scalar.DURATION;
    } else if (type instanceof ListType list) {
      return new Type.ListOf(aquery(list.elemType()));
    } else if (type instanceof MapType map) {
      return new Type.MapOf(aquery(map.keyType()), aquery(map.valueType()));
    } else if (sources.containsKey(type.name())) {
      return sources.get(type.name());
    }
    return Type.ANY;
  }

  /** Names a type in an error message, as CEL names it. */
  String describe(Type type) {
    return switch (type) {
      case Type.Scalar scalar -> scalar.name().toLowerCase();
      case Type.ListOf list -> "list(" + describe(list.element()) + ")";
      case Type.MapOf map -> "map(" + describe(map.key()) + ", " + describe(map.value()) + ")";
      case Type.Struct struct -> struct.name();
      case Type.Any ignored -> "dyn";
    };
  }

  @Override
  public ImmutableCollection<CelType> types() {
    return ImmutableList.copyOf(structs.values());
  }

  @Override
  public Optional<CelType> findType(String typeName) {
    return Optional.ofNullable(structs.get(typeName));
  }
}
