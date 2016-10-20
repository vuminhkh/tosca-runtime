package com.toscaruntime.ansible.connection;

import com.toscaruntime.artifact.ConnectionUtil;
import com.toscaruntime.exception.deployment.artifact.BadExecutorConfigurationException;
import org.apache.commons.lang.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AnsibleCommandBuilder {

    private Map<String, Object> environmentVariables = new LinkedHashMap<>();

    private Map<String, String> options = new LinkedHashMap<>();

    private String command;

    private Set<String> arguments = new LinkedHashSet<>();

    public AnsibleCommandBuilder(String command) {
        this.command = command;
        if (StringUtils.isBlank(this.command)) {
            throw new BadExecutorConfigurationException("Command cannot be null");
        }
    }

    private Map<String, String> filterEmptyValues(Map<String, String> map) {
        if (map == null) {
            return Collections.emptyMap();
        } else {
            return map;
        }
    }

    public AnsibleCommandBuilder export(String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            this.environmentVariables.put(name, value);
        }
        return this;
    }

    public AnsibleCommandBuilder exports(Map<String, String> exports) {
        this.environmentVariables.putAll(filterEmptyValues(exports));
        return this;
    }

    public AnsibleCommandBuilder option(String name, String value) {
        if (StringUtils.isNotBlank(value)) {
            this.options.put(name, value);
        }
        return this;
    }

    public AnsibleCommandBuilder options(Map<String, String> options) {
        this.options.putAll(filterEmptyValues(options));
        return this;
    }

    public AnsibleCommandBuilder flags(List<String> flags) {
        if (flags != null) {
            Map<String, String> convertedFlags = flags.stream().collect(Collectors.toMap(flag -> flag, flag -> ""));
            this.options.putAll(convertedFlags);
        }
        return this;
    }

    public AnsibleCommandBuilder argument(String argument) {
        if (StringUtils.isNotBlank(argument)) {
            this.arguments.add(argument);
        }
        return this;
    }

    public AnsibleCommandBuilder arguments(List<String> arguments) {
        if (arguments != null) {
            this.arguments.addAll(arguments.stream().filter(StringUtils::isNotBlank).collect(Collectors.toList()));
        }
        return this;
    }

    public String build() {
        StringBuilder commandBuilder = new StringBuilder();
        if (!environmentVariables.isEmpty()) {
            commandBuilder.append(ConnectionUtil.getSetEnvCommands(environmentVariables).stream().collect(Collectors.joining(";"))).append(";");
        }
        commandBuilder.append(command).append(" ");
        if (!options.isEmpty()) {
            commandBuilder.append(options.entrySet().stream().map(entry -> {
                if (StringUtils.isNotBlank(entry.getValue())) {
                    return "--" + entry.getKey() + "=" + entry.getValue();
                } else {
                    return "--" + entry.getKey();
                }
            }).collect(Collectors.joining(" ")));
        }
        if (!arguments.isEmpty()) {
            commandBuilder.append(" ").append(arguments.stream().collect(Collectors.joining(" ")));
        }
        return commandBuilder.toString().trim();
    }
}
