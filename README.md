# aquery

aquery compiles the arguments of a List request to SQL. It supports
[AIP-160](https://google.aip.dev/160) and [CEL](https://cel.dev) filters,
[AIP-132](https://google.aip.dev/132) ordering, [AIP-158](https://google.aip.dev/158) page tokens
and [AIP-157](https://google.aip.dev/157) read masks.

aquery checks the text from the client against the schema of a resource. Then it gives SQL
fragments and bound values. The SQL is portable ISO SQL. PostgreSQL, MySQL, SQL Server, Oracle and
H2 all run it. The design follows the `aip160` and `aip132` packages of
[LUCI](https://chromium.googlesource.com/infra/luci/luci-go).

## Installation

aquery is published to [GitHub Packages](https://github.com/zZHorizonZz/aquery/packages). Add the
repository and the dependency to your `pom.xml`:

<!-- x-release-please-start-version -->
```xml
<repositories>
  <repository>
    <id>github-aquery</id>
    <url>https://maven.pkg.github.com/zZHorizonZz/aquery</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>dev.horizon</groupId>
    <artifactId>aquery</artifactId>
    <version>0.2.0</version>
  </dependency>
</dependencies>
```
<!-- x-release-please-end -->

For CEL filters, also add `aquery-grammar-cel`. It brings [cel-java](https://github.com/google/cel-java)
and its dependencies. cel-java has no module names, thus put it on the class path:

<!-- x-release-please-start-version -->
```xml
<dependency>
  <groupId>dev.horizon</groupId>
  <artifactId>aquery-grammar-cel</artifactId>
  <version>0.2.0</version>
</dependency>
```
<!-- x-release-please-end -->

GitHub Packages needs a token even for public packages. Create a personal access token with the
`read:packages` scope, and add it to `~/.m2/settings.xml` under the same id:

```xml
<settings>
  <servers>
    <server>
      <id>github-aquery</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN</password>
    </server>
  </servers>
</settings>
```

In GitHub Actions, use `${{ github.actor }}` and `${{ secrets.GITHUB_TOKEN }}` instead.

Snapshots of `master` are published to the same repository as `0.x.y-SNAPSHOT` versions. Release
notes are in [CHANGELOG.md](CHANGELOG.md) and on the
[releases page](https://github.com/zZHorizonZz/aquery/releases).

## Example

```java
DatabaseTable users = new DatabaseTable(
    new Field.Builder("username").backend(new StringColumn("username")).filterable().sortable().readable().build(),
    new Field.Builder("locked").backend(new BoolColumn("locked")).filterable().readable().build(),
    new Field.Builder("create_time").backend(new TimestampColumn("create_time")).filterable().sortable().build());

Parameters parameters = new Parameters(ParameterStyle.QUESTION_MARK);

FilterParser filters = new EbnfFilterParser();
Filter filter = filters.parse("username : \"dan\" NOT locked = true");
String where = WhereClause.of(users, filter, "T", parameters);
// ((T.username LIKE ? ESCAPE '!') AND (NOT (T.locked = ?)))
// parameters.values(): ["%dan%", true]

List<OrderBy> order = OrderByClause.mergeWithDefaultOrder(
    List.of(new OrderBy(new FieldPath("username"), false)), Order.parse("create_time desc"));
String orderBy = OrderByClause.of(users, order, "T");
// ORDER BY T.create_time DESC, T.username

SelectClause.Result select = SelectClause.of(users, ReadMask.parse("username"), "T");
// T.username
```

## Filter languages

Each filter language has its own module and its own `FilterParser`:

| Module | Parser | Language |
| --- | --- | --- |
| `aquery-grammar-ebnf` | `EbnfFilterParser` | the [AIP-160](https://google.aip.dev/160) EBNF. The `aquery` module includes it. |
| `aquery-grammar-cel` | `CelFilterParser` | [CEL](https://cel.dev) |

The server makes the parser of the language that it accepts, and keeps it. All parsers give the
same `Filter` tree, thus the schema, the backends and the SQL are the same:

```java
Filter ebnf = new EbnfFilterParser().parse("username : \"dan\" NOT locked = true");
Filter cel = new CelFilterParser().parse("username.contains(\"dan\") && !locked");
// Both: ((T.username LIKE ? ESCAPE '!') AND (NOT (T.locked = ?)))
```

A parser is a `TranslatingFilterParser`: a `SyntaxParser<T>` reads the grammar into a syntax tree,
and a `FilterTranslator<T>` changes that tree into the `Filter` tree. To add a language, implement
the two interfaces.

The CEL parser supports the part of CEL that SQL can answer:

| CEL | AIP-160 |
| --- | --- |
| `a && b`, `a \|\| b`, `!a` | `a AND b`, `a OR b`, `NOT a` |
| `count >= 10`, `10 <= count` | `count >= 10` |
| `locked` | `locked = true` |
| `name.contains("x")` | `name : "x"` |
| `name.startsWith("x")`, `name.endsWith("x")` | none |
| `status in [ACTIVE, PENDING]` | `status = ACTIVE OR status = PENDING` |
| `"prod" in tags` | `tags = "prod"` |
| `"site" in labels`, `has(labels.site)` | `labels:site` |
| `labels.site == "x"`, `labels["site"] == "x"` | `labels.site = "x"` |
| `age > duration("1m30s")` | `age > 90s` |
| `create_time > timestamp("2012-04-21T11:30:00Z")` | `create_time > "2012-04-21T11:30:00Z"` |

A CEL string has no wildcards: `name == "*.com"` compares with `=`. The parser refuses arithmetic,
`matches()`, `size()`, macros such as `exists()`, the conditional operator and `null`.

## Parameters

One `Parameters` keeps the bound values of one statement. Give the same instance to each clause
of the statement. Then the placeholders do not collide, and numbered placeholders continue from
one clause to the next. Each placeholder in the SQL binds its own value, in the sequence of the
text. Thus `?` works.

| Style | Placeholders | Driver |
| --- | --- | --- |
| `ParameterStyle.QUESTION_MARK` | `?` | JDBC |
| `ParameterStyle.DOLLAR` | `$1`, `$2` | PostgreSQL protocol |
| `ParameterStyle.AT_P` | `@p1`, `@p2` | SQL Server |
| `ParameterStyle.COLON` | `:1`, `:2` | Oracle |

Write your own `ParameterStyle` for a different driver.

## Page tokens

Continue a list with a page token:

```java
PageTokenCodec tokens = new PageTokenCodec(secretKey); // 16, 24 or 32 bytes
String fingerprint = PageToken.fingerprintOf(filter, order);

Optional<PageToken> token = tokens.decode(request.pageToken(), fingerprint);
String resume = token.map(t -> new Keyset(users, order).after(t.cursor(), "T", parameters)).orElse("(1 = 1)");
// ((T.create_time < ?) OR (T.create_time = ? AND T.username > ?))
// parameters.values(): [..., 2012-04-21T15:30Z, 2012-04-21T15:30Z, "dan"]

List<String> cursor = List.of(
    users.fields().get(2).backend().cursorText(lastCreateTime),
    users.fields().get(0).backend().cursorText(lastUsername));
String next = tokens.encode(new PageToken(cursor, fingerprint));
```

The backend of each field writes the text of its cursor value with `cursorText`, and reads it
again with `cursorValue`. The keyset binds the typed value, for example an `OffsetDateTime` for a
timestamp.

## Fields and backends

A field declares a `FieldBackend` that writes its SQL. The library has these backends:

| Backend | Column | Bound value |
| --- | --- | --- |
| `StringColumn` | VARCHAR | `String` |
| `OpaqueStringColumn` | VARCHAR with encoded values | `String` |
| `BoolColumn` | BOOLEAN | `Boolean` |
| `IntegerColumn` | BIGINT | `Long` |
| `DurationColumn` | BIGINT with nanoseconds | `Long` |
| `TimestampColumn` | TIMESTAMP WITH TIME ZONE | `OffsetDateTime` in UTC |
| `EnumColumn` | integer or VARCHAR, see `EnumColumn.Storage` | `Long` or `String` |
| `UuidColumn` | UUID | `UUID` |
| `RepeatedStringColumn` | array of VARCHAR | `String` |
| `KeyValueColumn` | array of `key:value` strings, or a child table | `String` |
| `SimpleColumn` | any, sort only | `String` cursor |

LIKE patterns use `!` as the escape character, as in `T.name LIKE ? ESCAPE '!'`.

`RepeatedStringColumn` and the `STRING_ARRAY` form of `KeyValueColumn` need array columns. Only
engines with arrays, for example PostgreSQL and H2, run their SQL. A child table works on all
engines:

```java
new Field.Builder("labels")
    .backend(new KeyValueColumn(new KeyValueColumn.ChildTable("user_labels", "user_id", "label_key", "label_value", "id")))
    .filterable()
    .build();
// labels.site = "pilsen":
// (EXISTS (SELECT 1 FROM user_labels aquery_kv WHERE aquery_kv.user_id = T.id AND aquery_kv.label_key = ? AND aquery_kv.label_value = ?))
```

To add a new type of field, write a new implementation of `FieldBackend`.

Text from the client goes into the statement only through bound values. Column names come only
from the schema. Thus the output is safe against SQL injection. Table aliases that start with
`aquery_` are reserved for the SQL that the library writes.

## Requirements

Java 25 or later.

## License

Apache License 2.0. The design comes from LUCI (infra/luci-go), The LUCI Authors. See `NOTICE`.
