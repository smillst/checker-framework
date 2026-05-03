import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Queue;
import org.checkerframework.checker.modifiability.qual.Modifiable;

// Tests that storing a modifiable subtype in a less-capable supertype does not permanently discard
// modifiability dimensions that the subtype supports.
class SubtypeCapabilityPreservationTest {

  void listIteratorThroughIterator() {
    @Modifiable ListIterator<String> listIterator = new LinkedList<String>().listIterator();
    @Modifiable Iterator<String> iterator = listIterator;
    @Modifiable ListIterator<String> roundTrip = (ListIterator<String>) iterator;
    roundTrip.add("added");
    roundTrip.set("replaced");
    roundTrip.remove();
  }

  void linkedListThroughQueue() {
    @Modifiable LinkedList<String> linkedList = new LinkedList<>();
    @Modifiable Queue<String> queue = linkedList;
    @Modifiable LinkedList<String> roundTrip = (LinkedList<String>) queue;
    roundTrip.add("added");
    roundTrip.set(0, "replaced");
    roundTrip.remove();
  }

  void arrayListThroughCollection() {
    @Modifiable ArrayList<String> arrayList = new ArrayList<>();
    @Modifiable Collection<String> collection = arrayList;
    @Modifiable ArrayList<String> roundTrip = (ArrayList<String>) collection;
    roundTrip.add("added");
    roundTrip.set(0, "replaced");
    roundTrip.remove(0);
  }
}
