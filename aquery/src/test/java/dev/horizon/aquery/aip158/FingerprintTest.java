package dev.horizon.aquery.aip158;

import static org.assertj.core.api.Assertions.assertThat;

import dev.horizon.aquery.aip132.Order;
import dev.horizon.aquery.aip160.Filter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FingerprintTest {

  @Test
  @DisplayName("fingerprints the parsed arguments, so spacing does not matter")
  void parsedArguments() {
    String spaced = PageToken.fingerprintOf(Filter.parse("a = \"x\"  b"), Order.parse("foo desc,  bar"), "publishers/1");
    String tight = PageToken.fingerprintOf(Filter.parse("a=\"x\" b"), Order.parse("foo desc,bar"), "publishers/1");

    assertThat(spaced).isEqualTo(tight);
  }

  @Test
  @DisplayName("a different filter, order or scope is a different listing")
  void differentArguments() {
    String base = PageToken.fingerprintOf(Filter.parse("a = \"x\""), Order.parse("foo"), "publishers/1");

    assertThat(PageToken.fingerprintOf(Filter.parse("a = \"y\""), Order.parse("foo"), "publishers/1")).isNotEqualTo(base);
    assertThat(PageToken.fingerprintOf(Filter.parse("a = \"x\""), Order.parse("foo desc"), "publishers/1")).isNotEqualTo(base);
    assertThat(PageToken.fingerprintOf(Filter.parse("a = \"x\""), Order.parse("foo"), "publishers/2")).isNotEqualTo(base);
    assertThat(PageToken.fingerprintOf(null, Order.parse("foo"), "publishers/1"))
        .isEqualTo(PageToken.fingerprintOf(Filter.parse(""), Order.parse("foo"), "publishers/1"));
  }
}
