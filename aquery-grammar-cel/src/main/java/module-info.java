module dev.horizon.aquery.cel {
  requires transitive dev.horizon.aquery.api;
  requires dev.horizon.aquery.shaded.cel;
  requires com.google.common;
  requires com.google.protobuf;

  exports dev.horizon.aquery.cel;
}
