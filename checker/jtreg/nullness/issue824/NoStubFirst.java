public class NoStubFirst {
  public static <T> void method(Supplier<T> supplier, Callable<? super T> callable) {}

  public interface Supplier<T> {}

  public interface Callable<T> {}
}
