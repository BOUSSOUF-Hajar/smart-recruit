package com.smartrecruit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTest {

    private final ApplicationModules modules = ApplicationModules.of(SmartRecruitApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        // Échoue si un module accède aux types internes d'un autre, ou en cas de cycle
        modules.verify();
    }
}
