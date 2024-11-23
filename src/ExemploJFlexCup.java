// projeto-automacao/src/ExemploJFlexCup.java
import java.io.File;
import java.io.FileReader;
import java_cup.runtime.Symbol;

public class AutomacaoTestesJFlexCup {
    public static void main(String[] args) {
        try {

            /*Comandos (no prompt):
            java -jar jflex-full-1.8.2.jar scanner.flex
            java -jar java-cup-11b.jar -parser Parser -symbols Tokens parser.cup
            */

            Runtime r = Runtime.getRuntime();
            Process p;            
            // gerar Scanner.java
            p = r.exec(new String[]{"java", "-jar", "lib/jflex-full-1.8.2.jar", "src/scanner.flex"}, null, new File("projeto-automacao"));
            System.out.println(p.waitFor()); //se ok, a saída será 0
            
            // Generate Parser.java and Tokens.java
            p = r.exec(new String[]{"java","-jar", "..\\java-cup-11b.jar", "-parser", "Parser", "-symbols", "Tokens", "..\\parser.cup"}, null, new File("src\\"));
            System.out.println(p.waitFor()); //se ok, a saída será 0
            
            Scanner scanner = new Scanner(new FileReader("projeto-automacao/entrada.txt"));
            System.out.println("Análise Léxica: Lista de Tokens:");
            Symbol s = scanner.next_token();
            while(s.sym != Tokens.EOF){
                System.out.printf("<%d, %s>\n", s.sym, s.value);
                s = scanner.next_token();
            }
            
            //criando o parser passando o scanner
            scanner = new Scanner(new FileReader("projeto-automacao/entrada.txt"));
            Parser parser = new Parser(scanner);
            parser.parse(); 
        }
        catch(Exception e) {
            System.out.println(e.getMessage());
        }
    }
}