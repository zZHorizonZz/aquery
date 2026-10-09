package dev.horizon.aquery.it.aip132;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.aquery.aip132.Order;
import dev.horizon.aquery.aip132.OrderBy;
import dev.horizon.aquery.aip132.OrderByClause;
import dev.horizon.aquery.it.ContractTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

abstract class OrderByContractTest extends ContractTest {

  static Stream<Arguments> orders() {
    return Stream.of(
        Arguments.of("id", List.of(1L, 2L, 3L, 4L, 5L)),
        Arguments.of("id desc", List.of(5L, 4L, 3L, 2L, 1L)),
        Arguments.of("count", List.of(4L, 5L, 1L, 2L, 3L)),
        Arguments.of("count desc", List.of(3L, 2L, 1L, 5L, 4L)),
        Arguments.of("age", List.of(3L, 1L, 5L, 2L, 4L)),
        Arguments.of("uid desc", List.of(5L, 4L, 3L, 2L, 1L)),
        Arguments.of("flag, id", List.of(2L, 4L, 1L, 3L, 5L)),
        Arguments.of("flag desc, count desc", List.of(3L, 1L, 5L, 2L, 4L)),
        Arguments.of("status, id desc", List.of(5L, 3L, 1L, 4L, 2L)),
        Arguments.of("status_name desc, age", List.of(2L, 4L, 3L, 1L, 5L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("orders")
  @DisplayName("sorts by each field and direction")
  void order(String orderBy, List<Long> expected) {
    assertThat(sorted(Order.parse(orderBy))).containsExactlyElementsOf(expected);
  }

  static Stream<Arguments> timestampOrders() {
    return Stream.of(
        Arguments.of("create_time", List.of(4L, 1L, 5L, 3L, 2L)),
        Arguments.of("create_time desc", List.of(2L, 3L, 5L, 1L, 4L)));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("timestampOrders")
  @DisplayName("sorts timestamps by their instant, not by their local time")
  void timestampOrder(String orderBy, List<Long> expected) {
    assertThat(sorted(Order.parse(orderBy))).containsExactlyElementsOf(expected);
  }

  @Test
  @DisplayName("the default order sorts the rows that the order of the client does not separate")
  void defaultOrder() {
    List<OrderBy> order = OrderByClause.mergeWithDefaultOrder(Order.parse("id desc"), Order.parse("flag"));

    assertThat(sorted(order)).containsExactly(4L, 2L, 5L, 3L, 1L);
  }

  private List<Long> sorted(List<OrderBy> order) {
    return ids("SELECT T.id FROM item T " + OrderByClause.of(table, order, "T"), List.of());
  }
}
