package javaexpression;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.checkerframework.framework.testchecker.javaexpression.qual.FlowExp;

public class Private {
  private final Map<String, Object> nameToPpt = new LinkedHashMap<>();

  public Collection<@FlowExp("nameToPpt") String> nameStringSet() {
    throw new RuntimeException();
  }
}
