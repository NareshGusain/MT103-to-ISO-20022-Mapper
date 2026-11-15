package com.example.mtiso.parser;

import java.util.Optional;

/**
 * Immutable envelope headers and trailers for a SWIFT FIN message.
 */
public record SwiftHeaders(
        String block1,
        String block2,
        String block3,
        String block5
) {
    public static SwiftHeaders empty() {
        return new SwiftHeaders(null, null, null, null);
    }

    public Optional<String> getBlock1() {
        return Optional.ofNullable(block1);
    }

    public Optional<String> getBlock2() {
        return Optional.ofNullable(block2);
    }

    public Optional<String> getBlock3() {
        return Optional.ofNullable(block3);
    }

    public Optional<String> getBlock5() {
        return Optional.ofNullable(block5);
    }
}

