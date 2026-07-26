// A modifiability sub-checker that is run on its own, rather than under the aggregate
// ModifiabilityChecker, checks the locations of @UnmodifiableParam annotations.  This test is run
// by GrowTest, which runs the Grow Checker alone.

import java.util.List;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;

class UnmodParamLocationStandaloneTest {

  // :: error: [unmodparam.location]
  @UnmodifiableParam List<String> field;

  void method(@UnmodifiableParam List<String> parameter) {}
}
