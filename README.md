<!--
  Copyright 2023, Gerwin Klein, Régis Décamps, Steve Rowe
  SPDX-License-Identifier: CC-BY-SA-4.0
-->

# JFlex

This directory contains JFlex, a fast scanner generator for Java.

To run JFlex, run `bin/jflex` from the command line or double click on the
jflex-full-1.9.1.jar file in the `lib/` directory.

See the manual in `doc/` or the website at <http://jflex.de> for more
information and for how to get started.


## Contents

    ├── BUILD.bazel      build specification for Bazel
    ├── changelog.md     summary of changes between releases
    ├── pom.xml          project object model to build with Maven
    ├── README.md        this file
    ├── bin              command line start scripts
    ├── doc              user manual
    ├── examples         example scanners and parsers
    ├── lib              syntax highlighting files ; also JFlex jar in binary distribution
    └── src              JFlex sources

----

# Linguagens Formais e Compiladores
Trabalho de linguagens formais e compiladores

## Grupo F - Especificação para automação de testes de software

### Arquivo de exemplo:

```java
definicao_de_cenarios {
    cenario_login {
        nome = "Login de usuário válido";
        dados_entrada = {
            usuario = "usuario@example.com";
            senha = "senha123";
        };
        acoes = {
            acessar_tela_login();
            preencher_campo_usuario(usuario);
            preencher_campo_senha(senha);
            clicar_botao_login();
        };
        resultados_esperados = {
            tela_principal_deve_estar_visivel();
            mensagem_de_bem_vindo_deve_ser_exibida();
        };
    }

    // ... outros cenários ...
}

definicao_de_configuracao {
    ambiente = "teste";
    url_base = "https://www.example.com";
    browser = "chrome";
}

teste_completo {
    nome = "Suite de testes de login";
    configuracao = ambiente_teste;
    cenarios = cenario_login;
}

//como opção de saída pode gerar o código correspondente em python com selenium
```


