/* projeto-automacao/src/scanner.flex */
import java_cup.runtime.Symbol;

%%
%public
%class Scanner
%unicode
%cup
%line
%char
%ignorecase
%eofval{
    return new Symbol(sym.EOF);
%eofval}

/* Regular expressions for tokens */
usuario = "usuario@example.com"
senha = "senha123"
action = "acessar_tela_login|preencher_campo_usuario|preencher_campo_senha|clicar_botao_login"
result = "tela_principal_deve_estar_visivel|mensagem_de_bem_vindo_deve_ser_exibida"

%% 

{usuario} { return new Symbol(sym.USUARIO); }
{senha} { return new Symbol(sym.SENHA); }
{action} { return new Symbol(sym.ACTION); }
{result} { return new Symbol(sym.RESULT); }
"=" { return new Symbol(sym.EQUALS); }
";" { return new Symbol(sym.SEMICOLON); }
. { /* Ignore other characters */ }