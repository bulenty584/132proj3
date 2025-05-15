class Shadowing {
    public static void main(String[] args) {
        C c;
        int _;
        c = new C();
        _ = c.initC(1, 2, 3, 4, 5, 6, 7);
        _ = c.initB(8, 9, 10, 11, 12, 13);
        _ = c.initA(14, 15, 16, 17);
        _ = c.printA();
         System.out.println(100);
        _ = c.printB();
        System.out.println(100);
        _ = c.printC();
    }
}

class A {
    int a;
    int ab;
    int ac;
    int abc;

    public int initA(int n1, int n2, int n3, int n4) {
        a = n1;
        ab = n2;
        ac = n3;
        abc = n4;
        return 0;
    }

    public int printA() {
        System.out.println(a);
        System.out.println(ab);
        System.out.println(ac);
        System.out.println(abc);
        return 0;
    }
}

class B extends A {
    int b;
    int ab;
    int bc;
    int abc;

    public int initB(int n1, int n2, int n3, int n4, int n5, int n6) {
        b = n1;
        ab = n2;
        bc = n3;
        abc = n4;
        a = n5;
        ac = n6;
        return 0;
    }

    public int printB() {
        System.out.println(a);
        System.out.println(b);
        System.out.println(ab);
        System.out.println(ac);
        System.out.println(bc);
        System.out.println(abc);
        return 0;
    }
}

class C extends B {
    int c;
    int ac;
    int bc;
    int abc;

    public int initC(int n1, int n2, int n3, int n4, int n5, int n6, int n7) {
        c = n1;
        ac = n2;
        bc = n3;
        abc = n4;
        a = n5;
        ab = n6;
        b = n7;
        return 0;
    }

    public int printC() {
        System.out.println(a);
        System.out.println(b);
        System.out.println(c);
        System.out.println(ab);
        System.out.println(ac);
        System.out.println(bc);
        System.out.println(abc);
        return 0;
    }
}