package org.chainlink.infrastructure.errorhandling;

import java.io.Serializable;
import java.util.Arrays;

import lombok.experimental.UtilityClass;

import static java.util.stream.Collectors.joining;

@UtilityClass
class AppExceptionUtil {
    static void validateArgs(int requiredArgs, Serializable... args) {
        if (args.length != requiredArgs) {
            String argsAsText = Arrays.stream(args)
                .map(String::valueOf)
                .collect(joining(","));

            throw new IllegalArgumentException(String.format(
                "Need %d arguments but got: %d: %s",
                requiredArgs, args.length, argsAsText
            ));
        }
    }
}
