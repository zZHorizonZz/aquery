# aquery

aquery compiles the arguments of a List request to SQL. It supports
[AIP-160](https://google.aip.dev/160) filters, [AIP-132](https://google.aip.dev/132) ordering,
[AIP-158](https://google.aip.dev/158) page tokens and [AIP-157](https://google.aip.dev/157) read
masks.

aquery checks the text from the client against the schema of a resource. Then it gives SQL
fragments and bound parameters. The design follows the `aip160` and `aip132` packages of
[LUCI](https://chromium.googlesource.com/infra/luci/luci-go).

## Example

```java
DatabaseTable users = new DatabaseTable(
    new Field.Builder("username").backend(new StringColumn("username")).filterable().sortable().readable().build(),
    new Field.Builder("locked").backend(new BoolColumn("locked")).filterable().readable().build(),
    new Field.Builder("create_time").backend(new TimestampColumn("create_time")).filterable().sortable().build());

Filter filter = Filter.parse("username : \"dan\" NOT locked = true");
WhereClause.Result where = WhereClause.of(users, filter, "T", "f_");
// ((T.username LIKE @f_0) AND (NOT (T.locked = TRUE)))

List<OrderBy> order = OrderByClause.mergeWithDefaultOrder(
    List.of(new OrderBy(new FieldPath("username"), false)), Order.parse("create_time desc"));
String orderBy = OrderByClause.of(users, order, "T");
// ORDER BY T.create_time DESC, T.username

SelectClause.Result select = SelectClause.of(users, ReadMask.parse("username"), "T");
// T.username
```

Continue a list with a page token:

```java
PageTokenCodec tokens = new PageTokenCodec(secretKey);   // 16, 24 or 32 bytes
String fingerprint = PageToken.fingerprintOf(filter, order);

Optional<PageToken> token = tokens.decode(request.pageToken(), fingerprint);
WhereClause.Result resume = token.map(t -> new Keyset(users, order).after(t.cursor(), "T", "k_")).orElse(null);
// ((T.create_time < 1335022200000000) OR (T.create_time = 1335022200000000 AND T.username > @k_0))

String next = tokens.encode(new PageToken(List.of(lastCreateTime, lastUsername), fingerprint));
```

## Fields and backends

A field declares a `FieldBackend` that writes its SQL. The library has `StringColumn`,
`BoolColumn`, `IntegerColumn`, `DurationColumn`, `TimestampColumn`, `EnumColumn`,
`RepeatedStringColumn`, `KeyValueColumn`, `OpaqueStringColumn` and `SimpleColumn`. To add a new
type of field, write a new implementation of `FieldBackend`.

Text from the client goes into the statement only through bound parameters. Column names come only
from the schema. Thus the output is safe against SQL injection.

## Requirements

Java 25 or later. No dependencies.

## License

Apache License 2.0. The design comes from LUCI (infra/luci-go), The LUCI Authors. See `NOTICE`.
