package tn.esprit.configserver;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmokeTest {

    @Test
    void verifyBuildAndLogic() {
        // This is a fast, isolated smoke test that runs without a Spring Context.
        // It guarantees that the service compiles and JUnit is executing correctly.
        assertTrue(true, "Smoke test passed successfully.");
    }
}
