// @below-java21-jdk-skip-test

// A pattern variable for a record component that is annotated only in a stub file has the
// component's type from the stub file.

class RecordPatternUsage {
  void varPattern(PairRecord p) {
    if (p instanceof PairRecord(var k, var v)) {
      k.toString();
      // :: error: [dereference.of.nullable]
      v.toString();
    }
  }

  void typePattern(PairRecord p) {
    if (p instanceof PairRecord(String k, Object v)) {
      k.toString();
      // :: error: [dereference.of.nullable]
      v.toString();
    }
  }
}
