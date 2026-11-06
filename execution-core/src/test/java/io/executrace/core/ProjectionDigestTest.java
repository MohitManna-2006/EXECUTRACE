package io.executrace.core;

import java.util.LinkedHashSet;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectionDigestTest {
    @Test void insertionOrderDoesNotChangeProjectionDigest() {
        var a = new LinkedHashSet<String>(); a.add("C-2"); a.add("C-1");
        var b = new LinkedHashSet<String>(); b.add("C-1"); b.add("C-2");
        OmsState left = new OmsState(Map.of(),Map.of(),Map.of(),Map.of(),a);
        OmsState right = new OmsState(Map.of(),Map.of(),Map.of(),Map.of(),b);
        assertThat(ReplayVerifier.digest(left)).isEqualTo(ReplayVerifier.digest(right));
    }
}
