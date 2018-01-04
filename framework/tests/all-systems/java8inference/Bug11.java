package inference.guava;

import java.io.Serializable;
import java.util.EnumMap;
import java.util.Map;

public class Bug11 {

    public static <K1, V2> MyMap<K1, V2> copyOf(Map<? extends K1, ? extends V2> map) {
        @SuppressWarnings("unchecked")
        MyMap<K1, V2> kvMap = (MyMap<K1, V2>) copyOfEnumMap((EnumMap<?, ?>) map);
        return kvMap;
    }

    private static <K2 extends Enum<K2>, V2> MyMap<K2, V2> copyOfEnumMap(
            EnumMap<K2, ? extends V2> original) {
        throw new RuntimeException();
    }

    public abstract static class MyMap<K3, V3> implements Map<K3, V3>, Serializable {}
}
