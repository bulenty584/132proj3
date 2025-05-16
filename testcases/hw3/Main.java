class Main {
    public static void main(String[] args) {
      Z a;
      a = new Z();
      System.out.println(a.f()); // expect 3
    }
  }


  class A {
    public int f() { return 1; }
  }
  class B extends A {
    public int f() { return 2; }
    public int g() { return this.f(); }
  }
  
  
  class C extends B {
  }
  class Z extends B {
  }
  