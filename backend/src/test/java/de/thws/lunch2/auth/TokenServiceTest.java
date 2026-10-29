package de.thws.lunch2.auth;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenServiceTest {
    private final TokenService tokens = new TokenService();

    @Test
    void generatedTokensContain32BytesEncodedAsUnpaddedBase64url() {
        Set<String> generated = new HashSet<>();
        for (int index = 0; index < 64; index++) {
            String token = tokens.newToken();
            assertTrue(token.matches("[A-Za-z0-9_-]{43}"));
            assertEquals(32, Base64.getUrlDecoder().decode(token).length);
            generated.add(token);
        }
        assertEquals(64, generated.size());
    }

    @Test
    void hashIsSha256LowercaseHex() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", tokens.hash("abc"));
        assertEquals(tokens.hash("abc"), tokens.hash("abc"));
        assertNotEquals(tokens.hash("abc"), tokens.hash("ABC"));
    }
}
