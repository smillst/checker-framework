import java.util.Date;
import org.checkerframework.checker.modifiability.qual.Modifiable;
import org.checkerframework.checker.modifiability.qual.PolyModifiable;
import org.checkerframework.checker.modifiability.qual.Unmodifiable;

interface MyGodMap<K, V> {
  // The actual definition.
  V getOrDefault(MyGodMap<K, V> this, Object key, V defaultValue);

  // With @PolyModifiable, which seems wrong.
  @PolyModifiable V getOrDefaultPoly(MyGodMap<K, V> this, Object key, @PolyModifiable V defaultValue);

  // Using a second type variable.
  <V2 extends V> V2 getOrDefault2(MyGodMap<K, V> this, Object key, V2 defaultValue);
}

public class GetOrDefaultTest {

  Date d;
  @Modifiable Date dm;
  @Unmodifiable Date du;

  void foo1(MyGodMap<String, Date> m) {
    Date d1 = m.getOrDefault("hello", d);
    @Modifiable Date d2 = m.getOrDefault("hello", d);
    @Unmodifiable Date d3 = m.getOrDefault("hello", d);
    Date d4 = m.getOrDefault("hello", dm);
    @Modifiable Date d5 = m.getOrDefault("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefault("hello", dm);
    Date d7 = m.getOrDefault("hello", du);
    @Modifiable Date d8 = m.getOrDefault("hello", du);
    @Unmodifiable Date d9 = m.getOrDefault("hello", du);
  }

  void foo2(MyGodMap<String, @Modifiable Date> m) {
    Date d1 = m.getOrDefault("hello", d);
    @Modifiable Date d2 = m.getOrDefault("hello", d);
    @Unmodifiable Date d3 = m.getOrDefault("hello", d);
    Date d4 = m.getOrDefault("hello", dm);
    @Modifiable Date d5 = m.getOrDefault("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefault("hello", dm);
    Date d7 = m.getOrDefault("hello", du);
    @Modifiable Date d8 = m.getOrDefault("hello", du);
    @Unmodifiable Date d9 = m.getOrDefault("hello", du);
  }

  void foo3(MyGodMap<String, @Unmodifiable Date> m) {
    Date d1 = m.getOrDefault("hello", d);
    @Modifiable Date d2 = m.getOrDefault("hello", d);
    @Unmodifiable Date d3 = m.getOrDefault("hello", d);
    Date d4 = m.getOrDefault("hello", dm);
    @Modifiable Date d5 = m.getOrDefault("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefault("hello", dm);
    Date d7 = m.getOrDefault("hello", du);
    @Modifiable Date d8 = m.getOrDefault("hello", du);
    @Unmodifiable Date d9 = m.getOrDefault("hello", du);
  }

  void foo4(MyGodMap<String, Date> m) {
    Date d1 = m.getOrDefaultPoly("hello", d);
    @Modifiable Date d2 = m.getOrDefaultPoly("hello", d);
    @Unmodifiable Date d3 = m.getOrDefaultPoly("hello", d);
    Date d4 = m.getOrDefaultPoly("hello", dm);
    @Modifiable Date d5 = m.getOrDefaultPoly("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefaultPoly("hello", dm);
    Date d7 = m.getOrDefaultPoly("hello", du);
    @Modifiable Date d8 = m.getOrDefaultPoly("hello", du);
    @Unmodifiable Date d9 = m.getOrDefaultPoly("hello", du);
  }

  void foo5(MyGodMap<String, @Modifiable Date> m) {
    Date d1 = m.getOrDefaultPoly("hello", d);
    @Modifiable Date d2 = m.getOrDefaultPoly("hello", d);
    @Unmodifiable Date d3 = m.getOrDefaultPoly("hello", d);
    Date d4 = m.getOrDefaultPoly("hello", dm);
    @Modifiable Date d5 = m.getOrDefaultPoly("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefaultPoly("hello", dm);
    Date d7 = m.getOrDefaultPoly("hello", du);
    @Modifiable Date d8 = m.getOrDefaultPoly("hello", du);
    @Unmodifiable Date d9 = m.getOrDefaultPoly("hello", du);
  }

  void foo6(MyGodMap<String, @Unmodifiable Date> m) {
    Date d1 = m.getOrDefaultPoly("hello", d);
    @Modifiable Date d2 = m.getOrDefaultPoly("hello", d);
    @Unmodifiable Date d3 = m.getOrDefaultPoly("hello", d);
    Date d4 = m.getOrDefaultPoly("hello", dm);
    @Modifiable Date d5 = m.getOrDefaultPoly("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefaultPoly("hello", dm);
    Date d7 = m.getOrDefaultPoly("hello", du);
    @Modifiable Date d8 = m.getOrDefaultPoly("hello", du);
    @Unmodifiable Date d9 = m.getOrDefaultPoly("hello", du);
  }

  void foo7(MyGodMap<String, Date> m) {
    Date d1 = m.getOrDefault2("hello", d);
    @Modifiable Date d2 = m.getOrDefault2("hello", d);
    @Unmodifiable Date d3 = m.getOrDefault2("hello", d);
    Date d4 = m.getOrDefault2("hello", dm);
    @Modifiable Date d5 = m.getOrDefault2("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefault2("hello", dm);
    Date d7 = m.getOrDefault2("hello", du);
    @Modifiable Date d8 = m.getOrDefault2("hello", du);
    @Unmodifiable Date d9 = m.getOrDefault2("hello", du);
  }

  void foo8(MyGodMap<String, @Modifiable Date> m) {
    Date d1 = m.getOrDefault2("hello", d);
    @Modifiable Date d2 = m.getOrDefault2("hello", d);
    @Unmodifiable Date d3 = m.getOrDefault2("hello", d);
    Date d4 = m.getOrDefault2("hello", dm);
    @Modifiable Date d5 = m.getOrDefault2("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefault2("hello", dm);
    Date d7 = m.getOrDefault2("hello", du);
    @Modifiable Date d8 = m.getOrDefault2("hello", du);
    @Unmodifiable Date d9 = m.getOrDefault2("hello", du);
  }

  void foo9(MyGodMap<String, @Unmodifiable Date> m) {
    Date d1 = m.getOrDefault2("hello", d);
    @Modifiable Date d2 = m.getOrDefault2("hello", d);
    @Unmodifiable Date d3 = m.getOrDefault2("hello", d);
    Date d4 = m.getOrDefault2("hello", dm);
    @Modifiable Date d5 = m.getOrDefault2("hello", dm);
    @Unmodifiable Date d6 = m.getOrDefault2("hello", dm);
    Date d7 = m.getOrDefault2("hello", du);
    @Modifiable Date d8 = m.getOrDefault2("hello", du);
    @Unmodifiable Date d9 = m.getOrDefault2("hello", du);
  }
}
