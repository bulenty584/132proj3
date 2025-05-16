class Main {
  public static void main(String[] args) {
    B b;
    b = new B();
    System.out.println(b.start());  // Expected: 143 (100 + 43)
  }
}

class A {
  public int foo() {
    return 43;
  }
}

class B extends A {
  public int foo() {
    return 100;
  }

  public int start() {
    return foo() + super_foo();
  }

  public int super_foo() {
    return new A().foo();  // simulate super.foo() since MiniJava doesn't have it
  }
}
