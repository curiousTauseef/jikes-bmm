public class NaiveSC {

  static class A {
    int fld;
    volatile int vfld;
  }

  static class B {
    int fld;
    volatile int vfld;
  }

  static A a = new A();
  static B b = new B();
  static int sfld1;
  static int sfld2;

  /** intra basic block: basic */
  static void test1() {
    int tmp;
    sfld1 = 42; // S
    sfld2 = 43; // S
    // fence
    tmp = sfld1; // L
    tmp = sfld2; // L
    sfld1 = 43; // S
    // fence
  }

  /** intra basic block: instance field load/store */
  static void test2() {
    int tmp;
    a.fld = 42;
    // fence
    b.fld = 43;
    // fence
    tmp = a.fld;
    tmp = b.fld;
    a.fld = 43;
    // fence
  }

  /** intra basic block: sync as fence */
  static void test3() {
    int tmp = 0;
    sfld1 = 42; // S
    synchronized (NaiveSC.class) { // sync
    }
    tmp = sfld2; // L
    sfld1 = 42; // S
    Utils.random(); // call
    tmp = sfld2; // L
  }

  /** inter basic block: basic */
  static void test4() {
    int tmp = 0;
    int unknown = Utils.random();
    sfld1 = 42; // S
    sfld2 = 43; // S
    if (unknown == 0) {
      sfld1 = 44; // S
      sfld2 = 45; // S
    } else {
      // fence
      tmp = sfld1; // L
      tmp = sfld2; // L
    }
    // fence
    tmp = sfld1; // L
    sfld1 = 43; // S
    // fence
  }

  /** inter basic block: no memory access block */
  static void test6() {
    int tmp = 0;
    int unknown = Utils.random();
    sfld1 = 42; // S
    sfld2 = 43; // S
    if (unknown == 0) {
      // no memory access block
    } else {
      // fence
      tmp = sfld1; // L
      tmp = sfld2; // L
    }
    // fence
    tmp = sfld1; // L
  }

  /** inter basic block: return and throw */
  static void test7() throws Exception {
    int tmp = 0;
    Exception t = Utils.createException();
    int unknown = Utils.random();
    sfld1 = 42; // S
    sfld2 = 43; // S
    if (unknown > 0) {
      unknown *= unknown;
      // fence
      return;
    } else if (unknown == 42) {
      // fence
      throw t;
    } else if (unknown < 0) {
      // fence
      tmp = sfld1; // L
      tmp = sfld2; // L
    } else {
      // fence
      tmp = sfld1; // L
    }
    tmp = sfld1; // L
  }

  /** loop: revisit BB */
  static void test8() {
    int tmp = 0;
    int unknown = Utils.random();
    tmp = sfld1; // L
    do {
      // fence
      tmp = sfld2; // L
      sfld1 = 42; // S
    } while (unknown == 0);
    // fence
  }

  /** loop: no mem acc BB */
  static void test9() {
    int tmp = 0;
    int unknown = Utils.random();
    tmp = sfld1; // L
    do {
      tmp *= unknown; // no mem acc BB
      if (tmp > 44) {
        // fence
        tmp = sfld1; // L
      } else {
        sfld1 = 42; // S
      }
      // fence
      tmp = sfld2; // L
      sfld1 = 42; // S
    } while (unknown == 0);
    // fence
  }

  /** exception: try and catch */
  static void test99() throws Exception {
    Exception t = Utils.createException();
    try {
      sfld1 = 42;
      throw t;
    } catch (Exception e) {
      sfld2 = 43;
      // fence
      throw e;
    }
  }
}
