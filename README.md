# If/Else Merge Branch
I included a test case that is failing and should not be.

Run `gradlew allResourceLeakTests`. Exactly one test should fail:

`java.lang.AssertionError: 28 out of 28 expected diagnostics were found.
 1 unexpected diagnostic was found:
  LoopIfElseMergeTest.java:22: error: (unfulfilled.collection.obligations)`

The LoopIfElseMergeTest.java file describes the issue and likely fix, which
requires adding a merge store in the cfg translation of loops.
