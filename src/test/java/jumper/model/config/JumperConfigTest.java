// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JumperConfigTest {

  private static final String ISSUER = "http://localhost:1081/auth/realms/default";

  static Stream<Arguments> isMeshRouteCases() {
    return Stream.of(
        // mesh flag, internalTokenEndpoint (issuer), expected isMeshRoute
        Arguments.of(Boolean.TRUE, ISSUER, true), // both signals -> mesh
        Arguments.of(Boolean.TRUE, null, true), // new config: mesh flag only
        Arguments.of(null, ISSUER, true), // legacy config: issuer only (fallback)
        Arguments.of(Boolean.FALSE, ISSUER, true), // explicit false but legacy issuer present
        Arguments.of(null, null, false), // real route: neither signal
        Arguments.of(Boolean.FALSE, null, false)); // explicit non-mesh
  }

  @ParameterizedTest
  @MethodSource("isMeshRouteCases")
  void isMeshRoute(Boolean mesh, String internalTokenEndpoint, boolean expected) {
    // arrange
    JumperConfig jc = new JumperConfig();
    jc.setMesh(mesh);
    jc.setInternalTokenEndpoint(internalTokenEndpoint);

    // act
    boolean result = jc.isMeshRoute();

    // assert
    assertEquals(expected, result);
  }

  @Test
  void isMeshRoute_defaultsToFalse() {
    // arrange
    JumperConfig jc = new JumperConfig();

    // act & assert
    assertEquals(false, jc.isMeshRoute());
  }
}
