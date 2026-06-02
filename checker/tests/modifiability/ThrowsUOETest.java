// @below-java21-jdk-skip-test

import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class ThrowsUOETest {

  void testSortedSet(SortedSet<String> s) {
    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    s.addFirst("foo");

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    s.addLast("bar");
  }

  void testImplementation(TreeSet<String> ts) {
    // Since TreeSet implements SortedSet, it inherits the methods.
    // We verify that calling them on a concrete implementation also triggers the
    // warning.

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    ts.addFirst("foo");

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    ts.addLast("bar");
  }

  void testSortedMap(SortedMap<String, String> m) {
    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putFirst("foo", "bar");

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putLast("baz", "qux");
  }

  void testImplementation(TreeMap<String, String> tm) {
    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    tm.putFirst("foo", "bar");

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    tm.putLast("baz", "qux");
  }

  void testNavigableMap(NavigableMap<String, String> m) {
    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putFirst("foo", "bar");

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putLast("baz", "qux");
  }

  void testConcurrentNavigableMap(ConcurrentNavigableMap<String, String> m) {
    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putFirst("foo", "bar");

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putLast("baz", "qux");
  }

  void testConcurrentSkipListMap(ConcurrentSkipListMap<String, String> m) {
    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putFirst("foo", "bar");

    // :: error: [usage.throws.uoe] :: error: [method.invocation]
    m.putLast("baz", "qux");
  }
}
