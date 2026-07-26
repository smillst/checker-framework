package org.checkerframework.checker.test.junit;

import java.io.File;
import java.util.List;
import org.checkerframework.checker.modifiability.grow.GrowChecker;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests the Grow Checker run on its own, rather than under the aggregate ModifiabilityChecker. Most
 * modifiability tests are in {@link ModifiabilityTest}; this test exists for behavior that differs
 * when a modifiability sub-checker is the outermost checker.
 */
public class GrowTest extends CheckerFrameworkPerDirectoryTest {

  /**
   * Create a GrowTest.
   *
   * @param testFiles the files containing test code, which will be type-checked
   */
  public GrowTest(List<File> testFiles) {
    super(testFiles, GrowChecker.class, "modifiability-grow", "-Anomsgtext");
  }

  @Parameters
  public static String[] getTestDirs() {
    return new String[] {"modifiability-grow"};
  }
}
