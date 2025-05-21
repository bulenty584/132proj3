class Main {
    public static void main(String[] args) {
        A a;
        B b;
        a = new B();  // upcast
        b = new B();
        System.out.println(a.init(b.set(42)));
    }
}

class A {
    int x;

    public int init(int val) {
        x = val;
        return x;
    }

    public int set(int v) {
        x = v + 1;
        return x;
    }
}

class B extends A {
    int x;  // shadows A.x

    public int set(int v) {
        x = v + 2;
        return x;
    }
}
