import java.util.List;
import java.util.function.Consumer;
import org.checkerframework.checker.modifiability.qual.UnmodifiableParam;

class UnmodParamLocationTest<
    // :: error: [unmodparam.location]
    @UnmodifiableParam T> {

  // :: error: [unmodparam.location]
  @UnmodifiableParam List<String> field;

  // :: error: [unmodparam.location]
  List<@UnmodifiableParam List<String>> nestedField;

  UnmodParamLocationTest(@UnmodifiableParam List<String> parameter) {}

  void method(@UnmodifiableParam List<String> parameter) {}

  void nestedParameter(List<@UnmodifiableParam List<String>> parameter) {}

  @SuppressWarnings("class.unverified")
  abstract static class RelevantReceiver implements List<String> {
    void receiver(@UnmodifiableParam RelevantReceiver this) {}
  }

  // :: error: [unmodparam.location]
  @UnmodifiableParam List<String> returnType() {
    return null;
  }

  @SuppressWarnings("unchecked")
  void local(Object object) {
    // :: error: [unmodparam.location]
    @UnmodifiableParam List<String> local = null;
    local = null;

    // :: error: [unmodparam.location]
    List<String> cast = (@UnmodifiableParam List<String>) object;
    cast = null;
  }

  void lambda() {
    // :: error: [unmodparam.location]
    Consumer<List<String>> consumer = (@UnmodifiableParam List<String> parameter) -> {};
    consumer.accept(null);
  }

  // :: error: [unmodparam.location]
  <@UnmodifiableParam S> void methodTypeParameter(S value) {}

  // A method declared within a method, to test that each method's permitted annotations apply only
  // to that method.
  void nestedMethod(@UnmodifiableParam List<String> parameter) {
    class Local {
      void innerParameter(@UnmodifiableParam List<String> innerParameter) {}

      void innerLocal() {
        // :: error: [unmodparam.location]
        @UnmodifiableParam List<String> local = null;
        local = null;
      }
    }
    new Local().innerLocal();
  }

  // A field declared after a method, to test that a method's permitted annotations do not leak into
  // the enclosing class.
  // :: error: [unmodparam.location]
  @UnmodifiableParam List<String> fieldAfterMethod;
}
