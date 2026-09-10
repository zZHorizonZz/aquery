package dev.horizon.aquery.aip160;

/**
 * The parent of the backends with an INT64 database column: integers, durations, timestamps and enum values.
 *
 * <p>
 * A cursor value of such a column is the decimal text of the INT64 value. The backend writes it as an integer
 * literal. Thus the comparison with the column has the correct type.
 */
abstract class Int64Column extends SimpleColumn {

  Int64Column(String databaseName) {
    super(databaseName);
  }

  @Override
  public String cursorArgument(String cursorValue, Generator generator) {
    return generator.literal(Long.parseLong(cursorValue));
  }
}
