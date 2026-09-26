package dev.horizon.aquery.aip160;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * The parent of the backends with a 64-bit integer database column, for example BIGINT: integers and durations.
 *
 * <p>
 * A restriction on such a column binds a {@link Long}. The text of a cursor is the decimal text of the integer.
 * Keyset pagination binds it as a {@link Long}. Thus the comparison with the column has the correct type.
 */
abstract class Int64Column extends SimpleColumn {

  Int64Column(String databaseName) {
    super(databaseName);
  }

  @Override
  public Object cursorValue(String cursorText) {
    return integerValue(cursorText);
  }

  @Override
  public String cursorText(Object value) {
    return integerText(value);
  }

  /**
   * Reads the decimal text of a 64-bit integer.
   *
   * @param cursorText the decimal text
   * @return the integer
   * @throws IllegalArgumentException if the text is not a 64-bit integer
   */
  static Long integerValue(String cursorText) {
    return Long.valueOf(cursorText);
  }

  /**
   * Writes the decimal text of an integer that the server read from a row.
   *
   * <p>
   * The driver can give the integer as a {@link Long}, an {@link Integer}, a {@link Short}, a {@link Byte}, a
   * {@link BigInteger} or a {@link BigDecimal} without a fraction.
   *
   * @param value the integer
   * @return the decimal text
   * @throws IllegalArgumentException if the value is not a 64-bit integer
   */
  static String integerText(Object value) {
    if (value instanceof Long || value instanceof Integer || value instanceof Short || value instanceof Byte) {
      return value.toString();
    }
    if (value instanceof BigInteger number && number.bitLength() < Long.SIZE) {
      return number.toString();
    }
    if (value instanceof BigDecimal number) {
      try {
        return Long.toString(number.longValueExact());
      } catch (ArithmeticException notAnInteger) {
        throw new IllegalArgumentException("'" + value + "' is not a 64-bit integer", notAnInteger);
      }
    }
    throw new IllegalArgumentException("'" + value + "' is not a 64-bit integer");
  }
}
