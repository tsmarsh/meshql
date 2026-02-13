package com.meshql.mesher;

import com.meshql.mesher.cli.ConvertCommand;
import com.meshql.mesher.cli.GenerateCommand;
import com.meshql.mesher.cli.IntrospectCommand;
import com.meshql.mesher.cli.RunCommand;
import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
        name = "mesher",
        description = "Generate MeshQL anti-corruption layer services from legacy databases",
        mixinStandardHelpOptions = true,
        version = "0.2.0",
        subcommands = {
                IntrospectCommand.class,
                ConvertCommand.class,
                GenerateCommand.class,
                RunCommand.class
        }
)
public class MesherCli implements Runnable {

    @Override
    public void run() {
        CommandLine.usage(this, System.out);
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new MesherCli()).execute(args);
        System.exit(exitCode);
    }
}
