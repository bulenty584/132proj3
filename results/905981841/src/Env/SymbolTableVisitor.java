package Env;

import minijava.syntaxtree.ClassDeclaration;
import minijava.syntaxtree.ClassExtendsDeclaration;
import minijava.syntaxtree.FormalParameter;
import minijava.syntaxtree.FormalParameterList;
import minijava.syntaxtree.Goal;
import minijava.syntaxtree.Identifier;
import minijava.syntaxtree.MainClass;
import minijava.syntaxtree.MethodDeclaration;
import minijava.syntaxtree.Node;
import minijava.syntaxtree.VarDeclaration;
import minijava.visitor.GJDepthFirst;

/**
 * SymbolTableVisitor to first pass through AST and build Symbol Table
 * Implements GJVoidDepthFirst to visit tree but no need to pass objects back and forth
 */




public class SymbolTableVisitor extends GJDepthFirst<Void, Void>  {
    private final SymbolTable table = new SymbolTable();
    private MiniJavaClass currentClass;

    public SymbolTable getTable(){
        return this.table;
    }

    private String findType(int typeIndex) {
        String varType;
        switch (typeIndex) {
            case 0:
                varType = "int[]";
                break;
            case 1:
                varType = "boolean";
                break;
            case 2:
                varType = "int";
                break;
            default:
                varType = "";
        }
        return varType;
    }
    

    /**
    * f0 -> MainClass()
    * f1 -> ( TypeDeclaration() )*
    * f2 -> <EOF>
    */
    @Override
   public Void visit(Goal n, Void argu) {
    n.f0.accept(this, argu);
    n.f1.accept(this, argu);
    n.f2.accept(this, argu);

    return null;
 }

    @Override
    public Void visit(MainClass n, Void argu) {

        //main class
        String className = n.f1.f0.toString();
        MiniJavaClass _class = new MiniJavaClass(className);
        table.add(_class);

        //define main method and main parameter
        Variable mainParameter = new Variable(n.f11.f0.toString(), "");
        MiniJavaMethod mainMethod = new MiniJavaMethod("main", new Variable("", ""));
        mainMethod.parameters.add(mainParameter);

        _class.addMethod(mainMethod);

        this.currentClass = _class;
        // run through type declarations
        for (Node node : n.f14.nodes){
            node.accept(this, argu);
        }

        return null;
    }

    @Override
    public Void visit(ClassDeclaration n, Void argu){
        String className = n.f1.f0.toString();

        //check if already defined
        if (table.classMap.get(className) != null){
            throw new RuntimeException("Duplicate class already defined");
        }


        //Add new class to table
        MiniJavaClass classToAdd = new MiniJavaClass(className);
        table.add(classToAdd);

        //Add field declarations
        this.currentClass = classToAdd;
        for (Node nd : n.f3.nodes){
            nd.accept(this, argu);
        }

        // Add method declarations
        for (Node nd : n.f4.nodes){
            nd.accept(this, argu);
        }

        return null;
    }

    @Override
    public Void visit(ClassExtendsDeclaration n, Void argu) {
        
        String className = n.f1.f0.toString();
        String parentName = n.f3.f0.toString();

        // Define child and parent classes
        MiniJavaClass child = new MiniJavaClass(className);
        MiniJavaClass parent = table.classMap.get(parentName);

        //Can preload parent in MiniJava
        if (parent == null){
            parent = new MiniJavaClass(parentName);
        }

        //Set parent and add child to table
        child.setParent(parent);
        table.add(child);

        // run through VarDeclarations
        this.currentClass = child;
        for (Node nd : n.f5.nodes){
            nd.accept(this, argu);
        }

        // run through MethodDeclarations
        for (Node nd : n.f6.nodes){
            nd.accept(this, argu);
        }

        return null;
    }

    @Override
    public Void visit(VarDeclaration n, Void argu) {
        String varName = n.f1.f0.toString();

        // Extract type as string
        String varType;
        if (n.f0.f0.choice instanceof Identifier) {
            // user-defined class type (IDENTIFIER)
            varType = ((Identifier) n.f0.f0.choice).f0.toString();
        } else {
            varType = findType(n.f0.f0.which);
        }

        Variable var = new Variable(varName, varType);

        MiniJavaMethod currentMethod = this.currentClass.currentMethod == null
            ? null
            : this.currentClass.currentMethod;

        if (currentMethod != null) {
            // Check for parameter shadowing
            for (Variable param : currentMethod.parameters) {
                if (param.name.equals(varName)) {
                    throw new RuntimeException("Variable '" + varName + "' cannot shadow method parameter");
                }
            }
            if (!currentMethod.localVars.add(var)) {
                throw new RuntimeException("Duplicate local variable: " + varName);
            }
        } else {
            if (!this.currentClass.addField(var)) {
                throw new RuntimeException("Duplicate field: " + varName);
            }
        }

        return null;
    }


    @Override
    public Void visit(MethodDeclaration n, Void argu) {
        String methodName = n.f2.f0.toString();
        Variable returnType = new Variable(null, findType(n.f1.f0.which));

        if (returnType.type.equals("")){
            String actualType = ((Identifier) n.f1.f0.choice).f0.toString();
            returnType.type = actualType;
        }

        // Check if current class has method (prevent overloading)
        if (this.currentClass.methods.get(methodName) != null){
            throw new RuntimeException("Overloading not allowed for method: " + methodName);
        } else{
            //Define new method
            MiniJavaMethod method = new MiniJavaMethod(methodName, returnType);
            this.currentClass.addMethod(method);
        }

        n.f4.accept(this, argu);
        for (Node n7 : n.f7.nodes){
            n7.accept(this, argu);
        }
        return null;
    }

    @Override
    public Void visit(FormalParameter n, Void argu) {

        // Define param
        Variable typeParam = new Variable(n.f1.f0.toString(), findType(n.f0.f0.which));

        // extract actualType (class type) if it is a variable
        if (typeParam.type.equals("")){
            String actualType = ((Identifier) n.f0.f0.choice).f0.toString();
            typeParam.type = actualType;
        }

        // get current method and try to add parameter
        MiniJavaMethod method = this.currentClass.currentMethod;
        if (!method.parameters.add(typeParam)){
            throw new RuntimeException("Duplicate Parameter: " + typeParam.name);
        }

        return null;
    }


    // Run over all Formal Parameters
    @Override
    public Void visit(FormalParameterList n, Void argu) {
        MiniJavaMethod currentMethod = this.currentClass.currentMethod;

        if (currentMethod == null) {
            throw new RuntimeException("No method scope");
        }

        n.f0.accept(this, argu);
        for (Node rest : n.f1.nodes) {
            rest.accept(this, argu);
        }

        return null;
    }
}