# aquery

aquery compiles the arguments of a List request to SQL. It supports
[AIP-160](https://google.aip.dev/160) filters, [AIP-132](https://google.aip.dev/132) ordering and
[AIP-158](https://google.aip.dev/158) page tokens.

aquery parses the text from the client. It checks the text against the schema of a resource. Then
it gives SQL fragments and bound parameters. It does not know the database engine or the table.

The design follows the `aip160` and `aip132` packages of
[LUCI](https://chromium.googlesource.com/infra/luci/luci-go). LUCI is the build and CI platform of
Google. It implements the same API Improvement Proposals in Go.

## What it does

- **AIP-160 filters.** `Filter.parse(text)` reads the filter text into an AST.
  `WhereClause.of(table, filter, alias, prefix)` checks the AST against the fields of the resource.
  It gives a boolean SQL expression and its parameters. The parameter names start with the prefix.
- **AIP-132 order by.** `Order.parse(text)` reads an order_by clause with AIP-161 field paths.
  `OrderByClause.of(table, order, alias)` writes the `ORDER BY` clause from the sort keys of each
  field. `mergeWithDefaultOrder` adds a default order after the order of the client.
- **AIP-158 page tokens.** `PageTokenCodec` encrypts and authenticates a `PageToken` with AES-GCM.
  The client cannot read or change the token. The fingerprint of the token ties it to one list.
- **Keyset pagination.** `new Keyset(table, order).after(cursor, alias, prefix)` writes the
  condition that continues a list after the last row of a page.

Text from the client goes into the statement only through bound parameters. Numbers and booleans
are literals that the backends parsed. Column names come only from the table that the server
declares. Thus the output is safe against SQL injection.

## Example

```java
DatabaseTable users = new DatabaseTable(
    new Field.Builder("username").backend(new StringColumn("username")).filterable().sortable().build(),
    new Field.Builder("locked").backend(new BoolColumn("locked")).filterable().build(),
    new Field.Builder("create_time").backend(new TimestampColumn("create_time"))
        .filterable().sortable().build());

Filter filter = Filter.parse("username : \"dan\" NOT locked = true");
WhereClause.Result predicate = WhereClause.of(users, filter, "T", "f_");
// ((T.username LIKE @f_0) AND (NOT (T.locked = TRUE)))
// parameters: [f_0=%dan%]

List<OrderBy> order = OrderByClause.mergeWithDefaultOrder(
    List.of(new OrderBy(new FieldPath("username"), false)), Order.parse("create_time desc"));
String orderBy = OrderByClause.of(users, order, "T");
// ORDER BY T.create_time DESC, T.username
```

Continue a list with a page token:

```java
PageTokenCodec tokens = new PageTokenCodec(secretKey);   // 16, 24 or 32 bytes
String fingerprint = PageToken.fingerprintOf(filter, order);

// Read the token of the request. An empty token means the first page.
Optional<PageToken> token = tokens.decode(request.pageToken(), fingerprint);
WhereClause.Result resume = token
    .map(t -> new Keyset(users, order).after(t.cursor(), "T", "k_"))
    .orElse(null);
// ((T.create_time < 1335022200000000) OR (T.create_time = 1335022200000000 AND T.username > @k_0))

// Make the token of the next page from the last row.
String next = tokens.encode(new PageToken(List.of(lastCreateTime, lastUsername), fingerprint));
```

## Fields and backends

A field is not always a column of the table. The field `username` can be the column `username`.
Another field can come from several columns, or from one column and the clock. Thus a field
declares a `FieldBackend` that writes its SQL:

- `StringColumn`, `BoolColumn`, `IntegerColumn`, `DurationColumn`, `TimestampColumn`, `EnumColumn`,
  `RepeatedStringColumn`, `KeyValueColumn`, `OpaqueStringColumn` and `SimpleColumn`.
- Each backend declares its operators, and if it accepts nested fields, bare values and sorting.
  The library refuses all other uses. A `Field` checks the declaration when the server makes the
  schema. Thus a configuration error occurs at startup.
- To add a new type of field, write a new implementation of `FieldBackend`. You do not change the
  generator, the table or an enum.

The literal rules follow AIP-160. Strings are in double quotes. Booleans, integers, durations
(`1.5s`) and enum values have no quotes. Timestamps are RFC 3339 strings in double quotes. If a
literal has the wrong form, the error is INVALID_ARGUMENT and names the field.

## Limits and differences from AIP-160

AIP-160 tells a service to document its limits and differences. aquery has these:

- The filter text can have at most 16 KB of characters (`Filter.MAX_LENGTH`).
- Parentheses can have at most 64 levels (`Filter.MAX_DEPTH`).
- The parser does not support functions. It refuses a word immediately before `(`.
- The has operator (`:`) matches a substring on strings, on the elements of repeated strings and on
  the values of key-value fields. This is the LUCI behavior. AIP-160 says that `r:42` and `m.foo:42`
  compare for equality.
- The presence forms `r:*`, `m:*`, `m:foo` and `m.foo:*` are available on repeated string and
  key-value fields.
- A `*` wildcard is available only at the start or at the end of a string in an equality. A `*` at
  a different location is a usual character.
- No backend reads floats. An integer field refuses `2.5` with a clear error.
- A duration cannot be negative. A timestamp must have seconds and a UTC offset or `Z`.
- The keys of a key-value field in a string array cannot contain `:`.

## Differences from LUCI

- Added: string order comparisons, wildcards, presence forms, negative integers, timestamps, page
  tokens and keyset pagination.
- The lexer and the parsers use no regular expressions. Errors give the offset in the text.
- The field lookup is linear in the length of the path. LUCI is quadratic.

## Requirements

Java 25 or later. No dependencies.

## License

Apache License 2.0. The design comes from LUCI (infra/luci-go), The LUCI Authors. See `NOTICE`.
