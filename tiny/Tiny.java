public class Tiny {
  public static void main(String[] args) throws Exception {
    System.out.println("Hello, world.");
    int laps = 0;
    while (true) {
      laps++;
      for (int i=0; i < 100; i++)
        System.out.write(i+laps);
      System.out.print(" ... ");
      Thread.sleep(1000);
    }
  }
}