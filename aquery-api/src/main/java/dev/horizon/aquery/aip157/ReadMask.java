package dev.horizon.aquery.aip157;

import dev.horizon.aquery.aip132.FieldPath;
import dev.horizon.aquery.common.ServiceProvider;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Stream;

/**
 * A read mask, as AIP-157 specifies: the fields that the client wants in the response.
 *
 * <p>
 * The client writes the paths of the fields with commas between them, for example {@code name,labels.site}. The
 * paths use the syntax of AIP-161. A segment can be in backticks, for example {@code labels.`site name`}. The
 * value {@code *} means all fields. An empty mask also means all fields, as AIP-157 specifies.
 *
 * <p>
 * The grammar is:
 *
 * <pre>
 * read_mask = [spaces] (all | path_list) [spaces]
 * all = "*"
 * path_list = field_path {[spaces] "," [spaces] field_path}
 * field_path = segment {"." segment}
 * segment = string | quoted_string
 * </pre>
 *
 * <p>
 * The mask does not support the {@code *} wildcard inside a path, for example {@code authors.*.given_name}.
 * AIP-161 lets a service leave out this wildcard. If a mask contains {@code *} together with paths, the mask means
 * all fields.
 *
 * <p>
 * Use {@link SelectClause} to write the columns of the mask. A view enum, as AIP-157 specifies it, is a mask that
 * the server declares for each value of the enum. Make these masks with the constructors.
 *
 * <p>
 * The mask removes paths that occur two times. The text can have at most {@code MAX_LENGTH} (16 KB) characters.
 */
public record ReadMask(boolean allFields, List<FieldPath> paths) {

  public static final int MAX_LENGTH = 16 * 1024;

  private static final ServiceProvider<ReadMaskParser> PARSER = new ServiceProvider<>(ReadMaskParser.class);

  public ReadMask {
    paths = allFields ? List.of() : List.copyOf(new LinkedHashSet<>(paths));
    if (paths.isEmpty()) {
      allFields = true;
    }
  }

  public ReadMask() {
    this(true, List.of());
  }

  public ReadMask(List<FieldPath> paths) {
    this(false, paths);
  }

  /**
   * Parses the read mask that a client wrote.
   *
   * <p>
   * The parser comes from the module path or the class path at runtime. The internal module supplies it.
   *
   * @param readMask the text from the client. Null or blank text means all fields
   * @return the parsed read mask
   * @throws InvalidReadMaskException if the parser cannot read the text, or if the text is longer than {@code MAX_LENGTH}
   */
  public static ReadMask parse(String readMask) {
    if (readMask != null && readMask.length() > MAX_LENGTH) {
      throw new InvalidReadMaskException("the read mask is too long: %d characters, at most %d", readMask.length(), MAX_LENGTH);
    }
    return PARSER.get().parse(readMask);
  }

  /**
   * Gives a mask that also contains the paths.
   *
   * <p>
   * Use this method for the fields that the server always needs, for example the id or the sort keys of a
   * list. A mask of all fields already contains all paths.
   *
   * @param more the paths to add
   * @return this mask if it contains all fields, otherwise a mask with the paths of this mask and the added paths
   */
  public ReadMask including(FieldPath... more) {
    if (allFields) {
      return this;
    }
    List<FieldPath> merged = new ArrayList<>(paths);
    merged.addAll(Stream.of(more).toList());
    return new ReadMask(merged);
  }
}
