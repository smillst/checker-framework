package open.falsepos;

import java.io.Serializable;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;

public class Issue7364 {

  public static void test(Stream<GoalElement> goalElements) {
    Stream<AbstractGoal> abstractGoalStream = goalElements.map(AbstractGoal.class::cast);
  }

  public static void test2(Stream<@Nullable GoalElement> goalElements) {
    Stream<@Nullable AbstractGoal> abstractGoalStream = goalElements.map(AbstractGoal.class::cast);
    // : error: [methodref.return]
    Stream<AbstractGoal> abstractGoalStream2 = goalElements.map(AbstractGoal.class::cast);
  }

  public abstract static class AbstractGoal extends GoalElement {}

  public abstract static class GoalElement implements Serializable {}
}
