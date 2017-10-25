// Test case for issue #238: https://github.com/typetools/checker-framework/issues/238

import java.util.ArrayList;
import java.util.List;

class TestInfer {
    <T extends Object> T getValue(List<T> l) {
        return l.get(0);
    }

    void bar(Object o) {}

    void foo() {
        List<? extends Object> ls = new ArrayList<>();
        bar(getValue(ls));
        List<?> ls2 = new ArrayList<>();
        bar(getValue(ls2));
    }
}
