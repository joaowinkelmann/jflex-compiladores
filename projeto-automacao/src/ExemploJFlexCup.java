// projeto-automacao/src/ExemploJFlexCup.java
import java.io.File;
import java.io.FileReader;
import java_cup.runtime.Symbol;

public class ExemploJFlexCup {
    public static void main(String[] args) {
        try {           
            Runtime r = Runtime.getRuntime();
            Process p;            
            // Generate Scanner.java
            p = r.exec(new String[]{"java", "-jar", "lib/jflex-full-1.8.2.jar", "src/scanner.flex"}, null, new File("projeto-automacao"));
            System.out.println(p.waitFor()); // If ok, the output will be 0
            
            // Generate Parser.java and Tokens.java
            p = r.exec(new String[]{"java", "-jar", "lib/java-cup-11b.jar", "-parser", "Parser", "-symbols", "Tokens", "src/parser.cup"}, null, new File("projeto-automacao"));
            System.out.println(p.waitFor()); // If ok, the output will be 0
            
            Scanner scanner = new Scanner(new FileReader("projeto-automacao/automacao-testes.txt"));
            System.out.println("Análise Léxica: Lista de Tokens:");
            Symbol s = scanner.next_token();
            while(s.sym != Tokens.EOF){
                System.out.printf("<%d, %s>\n", s.sym, s.value);
                s = scanner.next_token();
            }
            
            // Create the parser passing the scanner
            scanner = new Scanner(new FileReader("projeto-automacao/automacao-testes.txt"));
            Parser parser = new Parser(scanner);        
            parser.parse(); 
        }
        catch(Exception e) { System.out.println(e.getMessage());}
    }
}