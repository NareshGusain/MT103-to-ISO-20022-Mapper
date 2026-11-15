package com.example.mtiso.mapper;

import com.example.mtiso.parser.MT103Message;
import com.example.mtiso.parser.MT103Parser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class Pacs008ToMt103Test {

    private MT103Parser parser;
    private Pacs008ToMt103 reverseMapper;

    @BeforeEach
    void setUp() {
        parser = new MT103Parser();
        reverseMapper = new Pacs008ToMt103(parser);
    }

    private String loadResource(String path) {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Resource not found: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed reading resource: " + path, e);
        }
    }

    @Test
    @DisplayName("Reverse map golden_01_minimal.xml to valid MT103Message")
    void testReverseMapGolden01() {
        String xml = loadResource("/golden/golden_01_minimal.xml");

        MT103Message mt103 = reverseMapper.toMt103(xml);

        assertThat(mt103).isNotNull();
        assertThat(mt103.tag20()).isPresent();
        assertThat(mt103.tag20().get().value()).isEqualTo("SYNTH2026111501");
        assertThat(mt103.tag23B()).isPresent();
        assertThat(mt103.tag23B().get().value()).isEqualTo("CRED");
        assertThat(mt103.tag32A()).isPresent();
        assertThat(mt103.tag32A().get().value()).isEqualTo("261115EUR10500,00");
        assertThat(mt103.tag50K()).isPresent();
        assertThat(mt103.tag50K().get().lines()).containsExactly("/1234567890", "SYNTHETIC SERVICES NV");
        assertThat(mt103.tag59()).isPresent();
        assertThat(mt103.tag59().get().lines()).containsExactly("/9876543210", "GLOBAL TRADING LTD");
        assertThat(mt103.tag71A()).isPresent();
        assertThat(mt103.tag71A().get().value()).isEqualTo("SHA");

        // Headers check
        assertThat(mt103.headers().block1()).contains("BANKBEBBAXXX");
        assertThat(mt103.headers().block2()).contains("BANKDEFFXXXX");
    }

    @Test
    @DisplayName("Reverse map golden_02_charge_our.xml to MT103 with :71A:OUR and :70:")
    void testReverseMapGolden02() {
        String xml = loadResource("/golden/golden_02_charge_our.xml");

        MT103Message mt103 = reverseMapper.toMt103(xml);

        assertThat(mt103).isNotNull();
        assertThat(mt103.tag20()).isPresent();
        assertThat(mt103.tag20().get().value()).isEqualTo("SYNTH2026111502");
        assertThat(mt103.tag32A()).isPresent();
        assertThat(mt103.tag32A().get().value()).isEqualTo("261115EUR50000,00");
        assertThat(mt103.tag71A()).isPresent();
        assertThat(mt103.tag71A().get().value()).isEqualTo("OUR");
        assertThat(mt103.tag70()).isPresent();
        assertThat(mt103.tag70().get().lines()).containsExactly("/INV/2026-OUR-01");
    }

    @Test
    @DisplayName("Produce syntactically valid raw FIN text")
    void testToMt103Text() {
        String xml = loadResource("/golden/golden_01_minimal.xml");

        String finText = reverseMapper.toMt103Text(xml);

        assertThat(finText).contains("{1:F01BANKBEBBAXXX0000000000}");
        assertThat(finText).contains("{2:I103BANKDEFFXXXXN}");
        assertThat(finText).contains(":20:SYNTH2026111501");
        assertThat(finText).contains(":32A:261115EUR10500,00");
        assertThat(finText).endsWith("-}\r\n");
    }
}
