package dev.horizon.aquery.aip157;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.horizon.aquery.InvalidQueryException;
import dev.horizon.aquery.aip132.FieldPath;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ReadMaskTest {

  @ParameterizedTest(name = "[{0}]")
  @ValueSource(strings = { "*", "", "   ", " * ", "name,*", "*, name" })
  @DisplayName("an empty mask and the wildcard mean all fields")
  void allFields(String readMask) {
    ReadMask mask = ReadMask.parse(readMask);

    assertThat(mask.allFields()).isTrue();
    assertThat(mask.paths()).isEmpty();
  }

  @Test
  @DisplayName("a missing mask means all fields")
  void missingMask() {
    assertThat(ReadMask.parse(null)).isEqualTo(new ReadMask());
    assertThat(new ReadMask(List.of())).isEqualTo(new ReadMask());
  }

  @Test
  @DisplayName("reads the paths with commas between them")
  void paths() {
    ReadMask mask = ReadMask.parse("name, author.given_name ,labels.`site name`");

    assertThat(mask.allFields()).isFalse();
    assertThat(mask.paths()).containsExactly(new FieldPath("name"), new FieldPath("author", "given_name"),
        new FieldPath("labels", "site name"));
  }

  @Test
  @DisplayName("removes a path that occurs two times")
  void duplicates() {
    assertThat(ReadMask.parse("name,title,name").paths()).containsExactly(new FieldPath("name"), new FieldPath("title"));
  }

  @Test
  @DisplayName("adds the paths that the server always needs")
  void including() {
    ReadMask mask = ReadMask.parse("title").including(new FieldPath("name"), new FieldPath("title"));

    assertThat(mask.paths()).containsExactly(new FieldPath("title"), new FieldPath("name"));
    assertThat(new ReadMask().including(new FieldPath("name"))).isEqualTo(new ReadMask());
  }

  @ParameterizedTest(name = "[{0}] fails at {1}")
  @CsvSource(delimiter = '|', quoteCharacter = '\'', textBlock = """
      authors.*.given_name | 8
      name, | 5
      name title | 5
      `open | 0
      labels. | 7
      *.name | 1
      """)
  @DisplayName("refuses what it cannot read, at the position where it stopped")
  void refuses(String readMask, int position) {
    assertThatThrownBy(() -> ReadMask.parse(readMask))
        .isInstanceOf(InvalidReadMaskException.class)
        .satisfies(thrown -> {
          InvalidQueryException error = (InvalidQueryException) thrown;
          assertThat(error.field()).isEqualTo(InvalidQueryException.READ_MASK);
          assertThat(error.position()).isEqualTo(position);
        });
  }

  @Test
  @DisplayName("names the wildcard it does not support")
  void wildcards() {
    assertThatThrownBy(() -> ReadMask.parse("authors.*.given_name")).hasMessageContaining("wildcard '*' is not supported");
  }

  @Test
  @DisplayName("refuses a mask longer than the limit")
  void tooLong() {
    assertThatThrownBy(() -> ReadMask.parse("a,".repeat(ReadMask.MAX_LENGTH / 2) + "a"))
        .isInstanceOf(InvalidReadMaskException.class)
        .hasMessageContaining("too long");
  }
}
