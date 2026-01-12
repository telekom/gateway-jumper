// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

class SpectreConfigurationTest {

  private final SpectreConfiguration testInstance = new SpectreConfiguration();

  {
    // Initialize the jsonContentTypes with all supported MediaType values
    List<MediaType> supportedMediaTypes =
        List.of(
            MediaType.valueOf("application/json"),
            MediaType.valueOf("application/merge-patch+json"),
            MediaType.valueOf("application/json-patch+json"),
            MediaType.valueOf("application/json-patch-query+json"),
            MediaType.valueOf("application/problem+json"));
    testInstance.setJsonContentTypes(supportedMediaTypes);
  }

  @ParameterizedTest
  @MethodSource("mediaTypeProvider")
  void jsonContentTypesContains(MediaType mediaType) {

    // WHEN
    boolean result = testInstance.jsonContentTypesContains(mediaType);

    // THEN
    if (result) {
      assertThat(testInstance.getJsonContentTypes())
          .as("Check that MediaType '%s' is part of supported media types", mediaType)
          .contains(mediaType);
    } else {
      assertThat(testInstance.getJsonContentTypes())
          .as("Check that MediaType '%s' is not part of supported media types", mediaType)
          .doesNotContain(mediaType);
    }
  }

  @ParameterizedTest(name = "[{index}] {0} should match {1}")
  @MethodSource("mediaTypeWithCharsetProvider")
  void jsonContentTypesContains_withCharsetParameter(String baseType, String typeWithCharset) {

    // GIVEN - configuration contains only base types without charset
    MediaType baseMediaType = MediaType.valueOf(baseType);
    MediaType mediaTypeWithCharset = MediaType.valueOf(typeWithCharset);

    // WHEN
    boolean resultWithCharset = testInstance.jsonContentTypesContains(mediaTypeWithCharset);

    // THEN
    assertThat(resultWithCharset)
        .as(
            "MediaType '%s' with charset parameter should match configured base type '%s'",
            typeWithCharset, baseType)
        .isTrue();

    assertThat(testInstance.jsonContentTypesContains(baseMediaType))
        .as("Base MediaType '%s' should also match", baseType)
        .isTrue();
  }

  private static Stream<Arguments> mediaTypeWithCharsetProvider() {
    return Stream.of(
        Arguments.of("application/json", "application/json;charset=UTF-8"),
        Arguments.of("application/json", "application/json;charset=ISO-8859-1"),
        Arguments.of("application/merge-patch+json", "application/merge-patch+json;charset=UTF-8"),
        Arguments.of("application/json-patch+json", "application/json-patch+json;charset=UTF-8"),
        Arguments.of(
            "application/json-patch-query+json", "application/json-patch-query+json;charset=UTF-8"),
        Arguments.of("application/problem+json", "application/problem+json;charset=UTF-8"));
  }

  private static Stream<Arguments> mediaTypeProvider() {
    return getAllMediaTypes().stream().map(Arguments::of);
  }

  private static List<MediaType> getAllMediaTypes() {
    List<MediaType> mediaTypes = new ArrayList<>();
    Field[] fields = MediaType.class.getDeclaredFields();

    for (Field field : fields) {
      if (field.getType().equals(MediaType.class)) {
        try {
          MediaType mediaType = (MediaType) field.get(null);
          // Reconstruct the base media type without parameters
          mediaTypes.add(new MediaType(mediaType.getType(), mediaType.getSubtype()));
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Failed to access MediaType field", e);
        }
      }
    }

    mediaTypes.add(MediaType.valueOf("application/json-patch-query+json"));
    mediaTypes.add(MediaType.valueOf("application/json-patch+json"));
    mediaTypes.add(MediaType.valueOf("application/merge-patch+json"));
    mediaTypes.add(MediaType.valueOf("application/*"));
    mediaTypes.add(MediaType.valueOf("application/*+json"));

    return mediaTypes;
  }
}
