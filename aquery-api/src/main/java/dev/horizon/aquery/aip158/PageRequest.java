package dev.horizon.aquery.aip158;

import java.util.Objects;

/**
 * The arguments of a List request, as AIP-132 and AIP-158 specify.
 *
 * <p>
 * The request has the four arguments of a list as the client wrote them: the filter text, the order_by text, the
 * page size and the token that continues a list. Null text and blank text both mean that the client wrote
 * nothing. The page size must be positive.
 *
 * <p>
 * The texts stay texts here. Parse the filter with {@code Filter.parse} and the order with {@code Order.parse} if
 * the list uses them. Then the errors name the correct argument.
 */
public record PageRequest(String filter, String orderBy, int pageSize, String pageToken) {

  public PageRequest {
    if (pageSize <= 0) {
      throw new IllegalArgumentException("pageSize must be positive, was " + pageSize);
    }
    filter = Objects.requireNonNullElse(filter, "").strip();
    orderBy = Objects.requireNonNullElse(orderBy, "").strip();
    pageToken = Objects.requireNonNullElse(pageToken, "").strip();
  }
}
