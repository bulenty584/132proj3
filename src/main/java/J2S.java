import java.io.InputStream;
import java.util.ArrayList;

import Env.SymbolTableVisitor;
import Env.TranslationVisitor;
import Env.TypeCheckVisitor;
import minijava.MiniJavaParser;
import minijava.syntaxtree.Goal;



public class J2S {
    public static void main(String [] args){
        try{
            InputStream in = System.in;
            MiniJavaParser parser = new MiniJavaParser(in);
            Goal goal = parser.Goal();
            SymbolTableVisitor symbolTableVisitor = new SymbolTableVisitor();
            goal.accept(symbolTableVisitor, null);
            //symbolTableVisitor.getTable().print();
            TypeCheckVisitor typeCheckVisitor = new TypeCheckVisitor(symbolTableVisitor.getTable());
            goal.accept(typeCheckVisitor, null);
            ArrayList<String> statements = new ArrayList<>();
            TranslationVisitor translationVisitor = new TranslationVisitor(symbolTableVisitor.getTable());
            goal.accept(translationVisitor, null);

            for (String line : translationVisitor.getLines()){
                System.out.println(line);
            }
            
        } catch (Exception e){
            System.out.println("Type error" + e);
        }
    }

}