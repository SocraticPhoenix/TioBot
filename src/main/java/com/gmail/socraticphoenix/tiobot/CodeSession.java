package com.gmail.socraticphoenix.tiobot;

import com.gmail.socraticphoenix.tioj.Tio;
import com.gmail.socraticphoenix.tioj.TioRequest;

import java.util.ArrayList;
import java.util.List;

public class CodeSession {
    private String language;

    private StringBuilder code;
    private StringBuilder input;

    private List<String> args;
    private List<String> compFlags;
    private List<String> cmdFlags;

    public CodeSession(String language) {
        this.language = language;

        this.code = new StringBuilder();
        this.input = new StringBuilder();

        this.args = new ArrayList<>();
        this.compFlags = new ArrayList<>();
        this.cmdFlags = new ArrayList<>();
    }

    public TioRequest format() {
        return Tio.newRequest()
                .setCode(this.code.toString())
                .setLang(this.language)
                .setInput(this.input.toString())
                .setArguments(this.args.toArray(new String[this.args.size()]))
                .setCompilerFlags(this.compFlags.toArray(new String[this.args.size()]))
                .setCommandLineFlags(this.cmdFlags.toArray(new String[this.args.size()]));
    }

    public String view() {
        StringBuilder builder = new StringBuilder();
        builder.append("Code:\n").append(this.code.toString()).append("\n============\n\n");

        builder.append("Input:\n").append(this.input.toString()).append("\n============\n");

        builder.append("\nArguments: ").append(this.args);
        builder.append("\nCompiler Flags: ").append(this.compFlags);
        builder.append("\nCommand Flags: ").append(this.cmdFlags);

        return builder.toString();
    }

    public StringBuilder getCode() {
        return this.code;
    }

    public StringBuilder getInput() {
        return this.input;
    }

    public List<String> getArgs() {
        return this.args;
    }

    public List<String> getCompFlags() {
        return this.compFlags;
    }

    public List<String> getCmdFlags() {
        return this.cmdFlags;
    }
}
