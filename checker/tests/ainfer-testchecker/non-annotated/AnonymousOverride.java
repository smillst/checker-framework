// A test that inference does not crash on code that contains an
// anonymous class that overrides a method.

class AnonymousOverride {
  public static void main(String[] args) {
    (new SpecialThread<String>() {
          @Override
          public void run() {
            System.out.println("starting a thread!");
          }
        })
        .start();
  }

  private static class SpecialThread<T extends @NonNull Object> extends Thread {
    public T t;
  }
}
