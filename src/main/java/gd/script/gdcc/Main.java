package gd.script.gdcc;

import gd.script.gdcc.cli.GdccCommand;

public final class Main {
    private Main() {
    }

    static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        return GdccCommand.execute(args);
    }
}
