package com.toscaruntime.artifact;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ConnectionUtil {

    public static List<String> getSetEnvCommands(Map<String, String> env) {
        // Set envs
        if (env != null) {
            return env.entrySet().stream().filter(envEntry -> envEntry.getValue() != null).map(
                    envEntry -> "export " + envEntry.getKey() + "='" + envEntry.getValue() + "'"
            ).collect(Collectors.toList());
        } else {
            return new ArrayList<>();
        }
    }

    public static String readSheBang(Path script) throws IOException {
        return readSheBang(new InputStreamReader(Files.newInputStream(script), "UTF-8"));
    }

    private static String readSheBang(Reader script) throws IOException {
        try (BufferedReader reader = new BufferedReader(script)) {
            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (line.length() > 0) {
                    if (line.startsWith("#!")) {
                        return line;
                    } else {
                        // Not found return a default value
                        return "#!/bin/sh";
                    }
                }
                line = reader.readLine();
            }
            // Not found return a default value
            return "#!/bin/sh";
        }
    }

    public static String readInterpreterCommand(Reader script) throws IOException {
        String shebang = readSheBang(script);
        return shebang.substring(2);
    }
}
