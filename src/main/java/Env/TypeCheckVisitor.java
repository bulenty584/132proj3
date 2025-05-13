package Env;

import java.util.ArrayList;
import java.util.List;

import minijava.syntaxtree.AllocationExpression;
import minijava.syntaxtree.AndExpression;
import minijava.syntaxtree.ArrayAllocationExpression;
import minijava.syntaxtree.ArrayAssignmentStatement;
import minijava.syntaxtree.ArrayLength;
import minijava.syntaxtree.ArrayLookup;
import minijava.syntaxtree.AssignmentStatement;
import minijava.syntaxtree.Block;
import minijava.syntaxtree.BracketExpression;
import minijava.syntaxtree.ClassDeclaration;
import minijava.syntaxtree.ClassExtendsDeclaration;
import minijava.syntaxtree.CompareExpression;
import minijava.syntaxtree.Expression;
import minijava.syntaxtree.ExpressionList;
import minijava.syntaxtree.ExpressionRest;
import minijava.syntaxtree.FalseLiteral;
import minijava.syntaxtree.Goal;
import minijava.syntaxtree.Identifier;
import minijava.syntaxtree.IfStatement;
import minijava.syntaxtree.IntegerLiteral;
import minijava.syntaxtree.MainClass;
import minijava.syntaxtree.MessageSend;
import minijava.syntaxtree.MethodDeclaration;
import minijava.syntaxtree.MinusExpression;
import minijava.syntaxtree.Node;
import minijava.syntaxtree.NotExpression;
import minijava.syntaxtree.PlusExpression;
import minijava.syntaxtree.PrimaryExpression;
import minijava.syntaxtree.PrintStatement;
import minijava.syntaxtree.Statement;
import minijava.syntaxtree.ThisExpression;
import minijava.syntaxtree.TimesExpression;
import minijava.syntaxtree.TrueLiteral;
import minijava.syntaxtree.WhileStatement;
import minijava.visitor.GJDepthFirst;


/**
 * Actual TypeCheck visitor used in second pass to compare values in SymbolTable 
 * We use a GJDepthFirst that returns MiniJavaType for actual Type checking
 */


public class TypeCheckVisitor extends GJDepthFirst<String, Void> {
    final SymbolTable table;
    MiniJavaClass currentClass = null;
    MiniJavaMethod currentMethod = null;

    private boolean isPrimitive(String type) {
        return type.equals(TypeConstants.INT) || type.equals(TypeConstants.BOOLEAN) || type.equals(TypeConstants.INT_ARRAY);
    }

    private boolean isBool(String type){
        return type.equals(TypeConstants.BOOLEAN);
    }

    public TypeCheckVisitor(SymbolTable table){
        this.table = table;
    }



    @Override
    public String visit(Goal n, Void argu) {
        String _ret = null;

        // Fix dummy parents if child class has parent
        for (String child : this.table.classMap.keySet()) {
            MiniJavaClass currentParent = this.table.classMap.get(child).getParent();

            if (currentParent != null) {
                String parentName = currentParent.getName();
                MiniJavaClass trueParent = this.table.classMap.get(parentName);
                if (trueParent == null) {
                    throw new RuntimeException("Undefined parent class: " + parentName + " for child class: " + child);
                }

                // Replace the dummy parent with the true parent
                this.table.classMap.get(child).setParent(trueParent);

            }
        }

        //Check for acyclic inheritance
        if (this.table.checkAcyclicInheritance()){
            throw new RuntimeException("Inheritance cycle found");
        }

        // Run through main class
        _ret = n.f0.accept(this, argu);

        // Run through TypeDeclaration Nodes
        for (Node _node : n.f1.nodes){
            _ret = _node.accept(this, argu);
        }

        return _ret;
    }

    @Override
    public String visit(MainClass n, Void argu) {
        String _ret=null;

        // Look for main class
        String className = n.f1.f0.toString();
        MiniJavaClass mainClass = this.table.classMap.get(className);
        if (mainClass == null) {
            throw new RuntimeException("Main class not found in symbol table: " + className);
        }

        // Extract main method
        MiniJavaMethod mainMethod = mainClass.methods.get("main");
        if (mainMethod == null) {
            throw new RuntimeException("main method not found in class: " + className);
        }

        // Current class to run through is main
        this.currentClass = mainClass;
        mainClass.main = true;

        // Run through statements
        for (Node stmt : n.f15.nodes) {
            _ret = stmt.accept(this, argu);
        }

        return _ret;
    }

    @Override
    public String visit(ClassDeclaration n, Void argu) {
        String _ret=null;

        // Find class to add to table
        String classId = n.f1.accept(this, argu);
        MiniJavaClass classToAdd = this.table.classMap.get(classId);

        if (classToAdd == null){
            throw new RuntimeException("No class defined: " + classId);
        } else{
            // Set current class
            this.currentClass = classToAdd;
        }

        // run through MethodDeclarations
        for (Node f4 : n.f4.nodes){
            _ret = f4.accept(this, argu);
        }

        return _ret;
    }

    @Override
    public String visit(ClassExtendsDeclaration n, Void argu) {
        String _ret=null;

        // extract parent and child
        String childClassType = n.f1.accept(this, argu);
        String parentClassType = n.f3.accept(this, argu);

        // Get child and parent from table
        MiniJavaClass childClass = this.table.classMap.get(childClassType);
        MiniJavaClass parentClass = this.table.classMap.get(parentClassType);

        if (this.table.classMap.get(childClassType) == null || this.table.classMap.get(parentClassType) == null){
            throw new RuntimeException("Class could not be found! Either " + childClassType + "Or " + parentClassType);
        }

        // set parent for child and set currentClass for child
        childClass.setParent(parentClass);
        this.currentClass = childClass;

        // MethodDeclarations
        for (Node n6 : n.f6.nodes){
            _ret = n6.accept(this, argu);
        }

        return _ret;
    }

    @Override
    public String visit(MethodDeclaration n, Void argu) {
        String _ret=null;

        // extract methodName, currentClass, and the return type
        String methodName = n.f2.f0.toString();

        MiniJavaClass currClass = this.currentClass;
        MiniJavaMethod expectedMethod = currClass.getMethodIncludingInherited(methodName, this.table, true);
        MiniJavaMethod oldCurrentMethod = currClass.currentMethod;
        currClass.currentMethod = expectedMethod;
        String returnType = n.f10.accept(this, argu);

        // Check if the return type is a class type and actually exists in classMap
        if (!this.isPrimitive(returnType) && !this.table.classMap.containsKey(returnType)) {
            throw new RuntimeException("Return type not defined: " + returnType);
        }

        Variable methodDecReturnType = new Variable(null, returnType);

        // Check return type compatibility with declared method return type
        Variable expectedReturnType = expectedMethod.getReturnType();
        if (!this.table.isTypeCompatible(methodDecReturnType, expectedReturnType)) {
            throw new RuntimeException("Return type mismatch in method '" + methodName +
                "': expected " + expectedReturnType.type + ", got " + returnType);
        }

        // run through statements
        for (Node statement : n.f8.nodes){
            statement.accept(this, argu);
        }

        // Clean up
        currClass.currentMethod = oldCurrentMethod;
        return null;
    }

    @Override
    public String visit(ArrayAssignmentStatement n, Void argu) {
        String _ret = null;

        String arrayType = n.f0.accept(this, argu);
        String indexType = n.f2.accept(this, argu);
        String rhsType = n.f5.accept(this, argu);

        if (!arrayType.equals(TypeConstants.INT_ARRAY)) {
            throw new RuntimeException("Left-hand side must be of type int[], got: " + arrayType);
        }
        if (!indexType.equals(TypeConstants.INT)) {
            throw new RuntimeException("Index expression must be of type int, got: " + indexType);
        }
        if (!rhsType.equals(TypeConstants.INT)) {
            throw new RuntimeException("Right-hand side must be of type int, got: " + rhsType);
        }

        return _ret;
    }


    @Override
    public String visit(IfStatement n, Void argu) {
        String _ret=null;
        String condition = n.f2.accept(this, argu);
        if (!isBool(condition)){
            throw new RuntimeException("Conditional must be boolean");
        }

        n.f4.accept(this, argu);
        n.f6.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(WhileStatement n, Void argu) {
        String _ret=null;
        String condition = n.f2.accept(this, argu);
        if (!isBool(condition)){
            throw new RuntimeException("Condition in while loop must be boolean");
        }
        n.f4.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(PrintStatement n, Void argu) {
        String _ret=null;
        String print = n.f2.accept(this, argu);
        if (!print.equals(TypeConstants.INT)){
            throw new RuntimeException("only integers are printable");
        }
        return _ret;
    }

    @Override
    public String visit(PrimaryExpression n, Void argu) {
        String _ret = null;
        _ret = n.f0.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(Expression n, Void argu) {
        String _ret=null;
        _ret = n.f0.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(AndExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        if (!isBool(comp1) || !isBool(comp2)){
            throw new RuntimeException("boolean types expected for expression &&: " + comp1 + " " + comp2);
        }

        _ret = TypeConstants.BOOLEAN;
        return _ret;
    }

    @Override
    public String visit(CompareExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        if (!comp1.equals(TypeConstants.INT) || !comp2.equals(TypeConstants.INT)){
            throw new RuntimeException("int types expected for compare: " + comp1 + " " + comp2 + " ");
        }
        
        _ret = TypeConstants.BOOLEAN;
        return _ret;
    }

    @Override
    public String visit(PlusExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        if (!comp1.equals(TypeConstants.INT) || !comp2.equals(TypeConstants.INT)){
            throw new RuntimeException("int types expected for +: " + comp1 + " " + comp2);
        }
        _ret = TypeConstants.INT;
        return _ret;
    }

    @Override
    public String visit(MinusExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        if (!comp1.equals(TypeConstants.INT) || !comp2.equals(TypeConstants.INT)){
            throw new RuntimeException("int types expected for -: " + comp1 + " " + comp2);
        }

        _ret = TypeConstants.INT;
        return _ret;
    }

    @Override
    public String visit(TimesExpression n, Void argu) {
        String _ret=null;
        String comp1 = n.f0.accept(this, argu);
        String comp2 = n.f2.accept(this, argu);

        if (!comp1.equals(TypeConstants.INT) || !comp2.equals(TypeConstants.INT)){
            throw new RuntimeException("int types expected for *: " + comp1 + " " + comp2);
        }

        _ret = TypeConstants.INT;
        return _ret;
    }

    @Override
    public String visit(ArrayLookup n, Void argu) {
        String _ret=null;
        String arrayType = n.f0.accept(this, argu);

        if (!arrayType.equals(TypeConstants.INT_ARRAY)){
            throw new RuntimeException("Cannot access element of an array that is not of type int: " + arrayType);
        } 

        String indexType = n.f2.accept(this, argu);

        if (!indexType.equals(TypeConstants.INT)){
            throw new RuntimeException("Cannot index a non-integer: " + arrayType);
        }

        _ret = TypeConstants.INT;
        return _ret;
    }

    @Override
    public String visit(IntegerLiteral n, Void argu) {
        String _ret=null;
        _ret = TypeConstants.INT;
        return _ret;
    }

    @Override
    public String visit(Identifier n, Void argu) {
        String identifierName = n.f0.toString();
        MiniJavaClass currClass = this.currentClass;
    
        if (currClass == null) {
            throw new RuntimeException("No current class is set — identifier " + identifierName + " cannot be resolved.");
        }
    
        // 1. Check method scope: parameters and locals
        if (currClass.currentMethod != null) {
            MiniJavaMethod currentMethod = currClass.currentMethod;
    
            for (Variable param : currentMethod.parameters) {
                if (param.name.equals(identifierName)) {
                    return param.type;
                }
            }
    
            for (Variable local : currentMethod.localVars) {
                if (local.name.equals(identifierName)) {
                    return local.type;
                }
            }
        }
    
        // 2. Check fields in class and parents
        MiniJavaClass cls = currClass;
        while (cls != null) {
            for (Variable field : cls.fields) {
                if (field.name.equals(identifierName)) {
                    return field.type;
                }
            }

            if (cls.methods.get(identifierName) != null){
                return identifierName;
            }
            cls = cls.getParent();
        }


    
        // 3. Check if it's a class name
        if (this.table.classMap.containsKey(identifierName) || currClass.methods.get(identifierName) != null) {
            return identifierName; // Treat as class type
        }
    
        throw new RuntimeException("Identifier not found: " + identifierName);
    }
    

    @Override
    public String visit(TrueLiteral n, Void argu) {
        String _ret=null;
        _ret = TypeConstants.BOOLEAN;
        return _ret;
    }

    @Override
    public String visit(FalseLiteral n, Void argu) {
        String _ret=null;
        _ret = TypeConstants.BOOLEAN;
        return _ret;
    }

    @Override
    public String visit(ThisExpression n, Void argu) {
        String _ret=null;
        MiniJavaClass currentClass = this.currentClass;

        if (currentClass.main){
            throw new RuntimeException("Cannot call 'this' for main " + currentClass.getName());
        } else{
            _ret = currentClass.getName();
        }
        return _ret;
    }

    @Override
    public String visit(ArrayAllocationExpression n, Void argu) {
        String _ret=null;
        _ret = n.f3.accept(this, argu);

        if (!_ret.equals(TypeConstants.INT)){
            throw new RuntimeException("Must allocate integer amount for array");
        } else{
            _ret = TypeConstants.INT_ARRAY;
        }
        return _ret;
    }

    @Override
    public String visit(AllocationExpression n, Void argu) {
        String className = n.f1.f0.toString();

        if (isPrimitive(className)) {
            throw new RuntimeException("Cannot instantiate primitive type: " + className);
        }

        if (!this.table.classMap.containsKey(className)) {
            throw new RuntimeException("Cannot instantiate non-existent class: " + className);
        }

        return className;
    }

    @Override
    public String visit(NotExpression n, Void argu) {
        String _ret=null;
        _ret = n.f1.accept(this, argu);

        if (!isBool(_ret)){
            throw new RuntimeException("Boolean type expected for logical !: " + _ret);
        }

        return _ret;
    }

    @Override
    public String visit(BracketExpression n, Void argu) {
        String _ret=null;
        _ret = n.f1.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(Statement n, Void argu) {
        String _ret=null;
        _ret = n.f0.accept(this, argu);
        return _ret;
    }

    @Override
    public String visit(Block n, Void argu) {
        String _ret=null;

        for (Node node : n.f1.nodes){
            node.accept(this, argu);
        }
        return _ret;
    }

    @Override
    public String visit(ArrayLength n, Void argu) {
        String _ret=null;
        String arrayType = n.f0.accept(this, argu);

        if (!arrayType.equals(TypeConstants.INT_ARRAY)){
            throw new RuntimeException("Type must be of Array: " + arrayType);
        }
        _ret = TypeConstants.INT;
        return _ret;
    }

    @Override
    public String visit(MessageSend n, Void argu) {
        String _ret=null;
        String objectType = n.f0.accept(this, argu);

        if (objectType.equals(TypeConstants.VAR)){
            throw new RuntimeException("Invalid class instance");
        }
        
        if (isPrimitive(objectType)) {
            throw new RuntimeException("object: " + objectType + "Not an object, cannot call . on it");
        }

        // Get identifier class
        MiniJavaClass reqClass;
        reqClass = this.table.classMap.get(objectType);

        if (reqClass == null){
            throw new RuntimeException("Type " + objectType + " not found");
        }

        // current class to evaluate and method
        MiniJavaClass oldClass = this.currentClass;
        this.currentClass = reqClass;

        // extract method we are calling
        String methodName = n.f2.accept(this, argu);
        MiniJavaMethod reqMethod = reqClass.getMethodIncludingInherited(methodName, this.table, false);

        this.currentClass = oldClass;
        oldClass = this.currentClass;
        if (reqMethod == null){
            throw new RuntimeException("Method '" + methodName + "' not found in class '" + objectType + "'");
        }


        // Run through parameters to method and evaluate, adding to args list
        Variable returnType = reqMethod.getReturnType();
        List<String> actualArgs = new ArrayList<>();
        if (n.f4 != null) {
            ExpressionList exprList = (ExpressionList) n.f4.node;
            if (exprList != null){
                String first = exprList.f0.accept(this, argu);
                actualArgs.add(first);
                for (Node node : exprList.f1.nodes) {
                    ExpressionRest rest = (ExpressionRest) node;
                    String next = rest.f1.accept(this, argu);
                    actualArgs.add(next);
                }
            }
        }

        this.currentClass = reqClass;
        MiniJavaMethod oldCurrentMethod = this.table.classMap.get(objectType).currentMethod;
        this.table.classMap.get(objectType).currentMethod = reqMethod;

        // Expected parameter list
        List<Variable> formalParams = new ArrayList<>(reqMethod.parameters);
        if (actualArgs.size() != formalParams.size()) {
            throw new RuntimeException("Argument count mismatch in method " + methodName + ". Expected " + formalParams.size() + ", got " + actualArgs.size());
        }

        // check parameters against each other
        for (int i = 0; i < actualArgs.size(); i++) {
            String actual = actualArgs.get(i);
            Variable actualVar = new Variable(null, actual);
            Variable expected = formalParams.get(i);
            if (!this.table.isTypeCompatible(actualVar, expected)) {
                throw new RuntimeException("Argument type mismatch in method " + methodName + ". Expected " + expected.type + ", got " + actual);
            }
        }

        // clean up
        this.table.classMap.get(objectType).currentMethod = oldCurrentMethod;
        this.currentClass = oldClass;
        _ret = returnType.type;
        return _ret;
    }

    @Override
    public String visit(AssignmentStatement n, Void argu) {
        String _ret=null;

        MiniJavaClass currentClass = this.currentClass;
        MiniJavaMethod currentMethod = currentClass.getMethodIncludingInherited(currentClass.currentMethod.getMethodName(), this.table, false);

        Variable lhsType = null;
        String varName = n.f0.accept(this, argu);

        if (isPrimitive(varName)){
            lhsType = new Variable(null, varName);
        }

        if (this.table.classMap.get(varName) != null){
            lhsType = new Variable(null, varName);
        }

        // Search local variables
        for (Variable var : currentMethod.localVars) {
            if (var.name.equals(varName)) {
                lhsType = var;
                break;
            }
        }

        // If not found, search parameters
        if (lhsType == null) {
            for (Variable param : currentMethod.parameters) {
                if (param.name.equals(varName)) {
                    lhsType = param;
                    break;
                }
            }
        }

        // If still not found, search class fields
        if (lhsType == null) {
            for (Variable field : currentClass.fields) {
                if (field.name.equals(varName)) {
                    lhsType = field;
                    break;
                }
            }

            while (currentClass.getParent() != null){
                for (Variable field : currentClass.getParent().fields){
                    if (field.name.equals(varName)){
                        lhsType = field;
                        break;
                    }
                }
                currentClass = currentClass.getParent();
            }
        }

        if (lhsType == null){
            throw new RuntimeException("Variable " + varName + " undeclared");
        }

        String rhsType = n.f2.accept(this, argu);
        Variable rhsTypeVar = new Variable(null, rhsType);
        if (!this.table.isTypeCompatible(rhsTypeVar, lhsType)) {
            throw new RuntimeException("Type mismatch in assignment: cannot assign " + rhsType + " to " + lhsType.type);
        }

        return _ret;
    }

    @Override
    public String visit(ExpressionList n, Void argu) {
        String _ret=null;
        n.f0.accept(this, argu);
        n.f1.accept(this, argu);
        return _ret;
    }
}