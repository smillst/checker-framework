import java.util.List;
import java.util.function.Consumer;
import org.checkerframework.checker.modifiability.qual.UnmodParam;

class UnmodParamLocationTest<
    // :: error: [unmodparam.location]
    @UnmodParam T> {

  // :: error: [unmodparam.location]
  @UnmodParam List<String> field;

  // :: error: [unmodparam.location]
  List<@UnmodParam List<String>> nestedField;

  UnmodParamLocationTest(@UnmodParam List<String> parameter) {}

  void method(@UnmodParam List<String> parameter) {}

  void nestedParameter(List<@UnmodParam List<String>> parameter) {}

  void receiver(@UnmodParam UnmodParamLocationTest<T> this) {}

  // :: error: [unmodparam.location]
  @UnmodParam List<String> returnType() {
    return null;
  }

  @SuppressWarnings("unchecked")
  void local(Object object) {
    // :: error: [unmodparam.location]
    @UnmodParam List<String> local = null;
    local = null;

    // :: error: [unmodparam.location]
    List<String> cast = (@UnmodParam List<String>) object;
    cast = null;
  }

  void lambda() {
    // :: error: [unmodparam.location]
    Consumer<List<String>> consumer = (@UnmodParam List<String> parameter) -> {};
    consumer.accept(null);
  }

  // :: error: [unmodparam.location]
  <@UnmodParam S> void methodTypeParameter(S value) {}
}
