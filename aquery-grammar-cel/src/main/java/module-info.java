module dev.horizon.aquery.cel {
  requires transitive dev.horizon.aquery.api;
  requires dev.horizon.aquery.shaded.cel;
  requires com.google.common;
  requires com.google.protobuf;

  exports dev.horizon.aquery.cel;
  exports dev.horizon.aquery.cel.expression;
  exports dev.horizon.aquery.cel.expression.operation;
  exports dev.horizon.aquery.cel.filter;
  exports dev.horizon.aquery.cel.filter.operation;
}
