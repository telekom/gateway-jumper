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
