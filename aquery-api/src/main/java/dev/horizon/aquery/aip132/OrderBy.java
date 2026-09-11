package dev.horizon.aquery.aip132;

/**
 * One part of an AIP-132 order_by clause.
 *
 * <p>
 * An order_by clause is a comma-separated list of these parts. Each part names a field and the direction to
 * sort that field in. The direction defaults to ascending when the client does not write one.
 *
 * @see <a href="https://chromium.googlesource.com/infra/luci/luci-go/+/main/common/data/aip132/orderby_parser.go">LUCI aip132: orderby_parser.go (OrderBy)</a>
 */
public record OrderBy(FieldPath fieldPath, boolean descending) {
}
