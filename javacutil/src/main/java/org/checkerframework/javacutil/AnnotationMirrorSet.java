package org.checkerframework.javacutil;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NavigableSet;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.lang.model.element.AnnotationMirror;

/**
 * The Set interface defines many methods with respect to the equals method. This implementation of
 * Set violates those specifications, but fulfills the same property using {@link
 * AnnotationUtils#areSame} rather than equals.
 *
 * <p>For example, the specification for the contains(Object o) method says: "returns true if and
 * only if this collection contains at least one element e such that (o == null ? e == null :
 * o.equals(e))." The specification for {@link AnnotationMirrorSet#contains} is "returns true if and
 * only if this collection contains at least one element e such that (o == null ? e == null :
 * AnnotationUtils.areSame(o, e))".
 *
 * <p>AnnotationMirror is an interface and not all implementing classes provide a correct equals
 * method; therefore, the existing implementations of Set cannot be used.
 */
public class AnnotationMirrorSet implements NavigableSet<AnnotationMirror> {

    /** Backing set. */
    private NavigableSet<AnnotationMirror> shadowSet =
            new TreeSet<>(AnnotationUtils::compareAnnotationMirrors);

    /** Default constructor. */
    public AnnotationMirrorSet() {}

    public AnnotationMirrorSet(Collection<? extends AnnotationMirror> values) {
        this();
        this.addAll(values);
    }

    @Override
    public int size() {
        return shadowSet.size();
    }

    @Override
    public boolean isEmpty() {
        return shadowSet.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return o instanceof AnnotationMirror
                && AnnotationUtils.containsSame(shadowSet, (AnnotationMirror) o);
    }

    @Override
    public AnnotationMirror lower(AnnotationMirror annotationMirror) {
        return shadowSet.lower(annotationMirror);
    }

    @Override
    public AnnotationMirror floor(AnnotationMirror annotationMirror) {
        return shadowSet.floor(annotationMirror);
    }

    @Override
    public AnnotationMirror ceiling(AnnotationMirror annotationMirror) {
        return shadowSet.ceiling(annotationMirror);
    }

    @Override
    public AnnotationMirror higher(AnnotationMirror annotationMirror) {
        return shadowSet.higher(annotationMirror);
    }

    @Override
    public AnnotationMirror pollFirst() {
        return shadowSet.pollFirst();
    }

    @Override
    public AnnotationMirror pollLast() {
        return shadowSet.pollLast();
    }

    @Override
    public Iterator<AnnotationMirror> iterator() {
        return shadowSet.iterator();
    }

    @Override
    public NavigableSet<AnnotationMirror> descendingSet() {
        return shadowSet.descendingSet();
    }

    @Override
    public Iterator<AnnotationMirror> descendingIterator() {
        return shadowSet.descendingIterator();
    }

    @Override
    public NavigableSet<AnnotationMirror> subSet(
            AnnotationMirror fromElement,
            boolean fromInclusive,
            AnnotationMirror toElement,
            boolean toInclusive) {
        return shadowSet.subSet(fromElement, fromInclusive, toElement, toInclusive);
    }

    @Override
    public NavigableSet<AnnotationMirror> headSet(AnnotationMirror toElement, boolean inclusive) {
        return shadowSet.headSet(toElement, inclusive);
    }

    @Override
    public NavigableSet<AnnotationMirror> tailSet(AnnotationMirror fromElement, boolean inclusive) {
        return shadowSet.tailSet(fromElement, inclusive);
    }

    @Override
    public Comparator<? super AnnotationMirror> comparator() {
        return shadowSet.comparator();
    }

    @Override
    public SortedSet<AnnotationMirror> subSet(
            AnnotationMirror fromElement, AnnotationMirror toElement) {
        return shadowSet.subSet(fromElement, toElement);
    }

    @Override
    public SortedSet<AnnotationMirror> headSet(AnnotationMirror toElement) {
        return shadowSet.headSet(toElement);
    }

    @Override
    public SortedSet<AnnotationMirror> tailSet(AnnotationMirror fromElement) {
        return shadowSet.tailSet(fromElement);
    }

    @Override
    public AnnotationMirror first() {
        return shadowSet.first();
    }

    @Override
    public AnnotationMirror last() {
        return shadowSet.last();
    }

    @Override
    public Object[] toArray() {
        return shadowSet.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return shadowSet.toArray(a);
    }

    @Override
    public boolean add(AnnotationMirror annotationMirror) {
        if (contains(annotationMirror)) {
            return false;
        }
        shadowSet.add(annotationMirror);
        return true;
    }

    @Override
    public boolean remove(Object o) {
        if (o instanceof AnnotationMirror) {
            AnnotationMirror found = AnnotationUtils.getSame(shadowSet, (AnnotationMirror) o);
            return found != null && shadowSet.remove(found);
        }
        return false;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean addAll(Collection<? extends AnnotationMirror> c) {
        boolean result = true;
        for (AnnotationMirror a : c) {
            if (!add(a)) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        NavigableSet<AnnotationMirror> newSet =
                new TreeSet<>(AnnotationUtils::compareAnnotationMirrors);
        for (Object o : c) {
            if (contains(o)) {
                newSet.add((AnnotationMirror) o);
            }
        }
        if (newSet.size() != shadowSet.size()) {
            shadowSet = newSet;
            return true;
        }
        return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean result = true;
        for (Object a : c) {
            if (!remove(a)) {
                result = false;
            }
        }
        return result;
    }

    @Override
    public void clear() {
        shadowSet.clear();
    }

    /**
     * Returns a new {@link AnnotationMirrorSet} that contains {@code value}.
     *
     * @param value AnnotationMirror to put in the set
     * @return a new {@link AnnotationMirrorSet} that contains {@code value}.
     */
    public static AnnotationMirrorSet singleElementSet(AnnotationMirror value) {
        AnnotationMirrorSet newSet = new AnnotationMirrorSet();
        newSet.add(value);
        return newSet;
    }

    @Override
    public String toString() {
        return shadowSet.toString();
    }
}
