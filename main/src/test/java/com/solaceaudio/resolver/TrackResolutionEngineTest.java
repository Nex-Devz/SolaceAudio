package com.solaceaudio.resolver;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TrackResolutionEngineTest {

    @Test
    public void defaultProvidersIncludeJioSaavnFallback() throws Exception {
        TrackResolutionEngine engine = new TrackResolutionEngine(null);

        Field providersField = TrackResolutionEngine.class.getDeclaredField("providers");
        providersField.setAccessible(true);
        String[] providers = (String[]) providersField.get(engine);

        boolean hasJioSaavn = false;
        for (String provider : providers) {
            if (provider.startsWith("jssearch:")) {
                hasJioSaavn = true;
                break;
            }
        }

        assertTrue(hasJioSaavn, "DEFAULT_PROVIDERS must include jssearch fallback for reliable audio resolution");
    }

    @Test
    public void customProvidersArePreserved() throws Exception {
        String[] custom = new String[]{"ytsearch:{query}", "jssearch:{query}"};
        TrackResolutionEngine engine = new TrackResolutionEngine(custom);

        Field providersField = TrackResolutionEngine.class.getDeclaredField("providers");
        providersField.setAccessible(true);
        String[] providers = (String[]) providersField.get(engine);

        assertEquals(2, providers.length);
        assertEquals("ytsearch:{query}", providers[0]);
        assertEquals("jssearch:{query}", providers[1]);
    }
}
