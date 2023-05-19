package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class Lox {
    private static final Interpreter interpreter = new Interpreter();
    static boolean hadError = false;
    static boolean hadRuntimeError = false;

    // I suspect args means text symbols. So this args > 1 means it's dealing with a token.
    // If it finds a token, it prints "Usage: jlox []"?
    // if the token is exactly one character long, then there's a file that gets run?
    // otherwise the prompt gets run
    public static void main(String[] args) throws IOException {
        if (args.length > 1) {
          System.out.println("Usage: jlox [script]");
          System.exit(64);
        } else if (args.length == 1) {
          runFile(args[0]);
        } else {
        //runPrompt();
        runFile("test.lox");
        }
    }

    public static List<Stmt> getStatements(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return null;

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        // Stop if there was a resolution error.
        if (hadError) return null;

        return statements;
    }

    private static void runFile(String path) throws IOException {
        byte[] bytes = Files.readAllBytes(Paths.get(path));
        run(new String(bytes, Charset.defaultCharset()));
        // "Indicate an error in the exit code."
        if (hadError) System.exit(65);
        if (hadRuntimeError) System.exit(70);
    }

    // This part seems like it's just linking things. Telling the Readers what to read from.
    private static void runPrompt() throws IOException {
        InputStreamReader input = new InputStreamReader(System.in);
        BufferedReader reader = new BufferedReader(input);

        // this seems like the part where you can write terminal lines that lox will read.
        for (;;) {
            System.out.print("> ");
            String line = reader.readLine();
            if (line == null) break;
            run(line);
            // "We need to reset this flag in the interactive loop."
            hadError = false;
        }
    }
    private static void run(String source) {
        Scanner scanner = new Scanner(source);
        List<Token> tokens = scanner.scanTokens();
        Parser parser = new Parser(tokens);
        List<Stmt> statements = parser.parse();

        // Stop if there was a syntax error.
        if (hadError) return;

        Resolver resolver = new Resolver(interpreter);
        resolver.resolve(statements);

        // Stop if there was a resolution error.
        if (hadError) return;

        // for now, just returns to us the parsed syntax.
        interpreter.interpret(statements);
    }

    // The book says this part tells us which line errors occurred on.
    static void error(int line, String message) {
        report(line, "", message);
    }

    static void runtimeError(RuntimeError error) {
        System.err.println(error.getMessage() +
            "\n[line " + error.token.line + "]");
        hadRuntimeError = true;
    }

    private static void report(int line, String where,
                                String message) {
        System.err.println(
            "[line " + line + "] Error" + where + ": " + message);
        hadError = true;
    }

    // Reports an error token by showing the token's location and what it is.
    static void error(Token token, String message) {
        // Unless it's the EOF token, then say that it's at end.
        if (token.type ==TokenType.EOF) {
            report(token.line, " at end", message);
        } else {
            report(token.line, " at '" + token.lexeme + "'", message);
        }
    }
}