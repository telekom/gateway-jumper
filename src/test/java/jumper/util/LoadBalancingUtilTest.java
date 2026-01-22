// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jumper.exception.LoadBalancingException;
import jumper.model.config.Server;
import org.junit.jupiter.api.Test;

class LoadBalancingUtilTest {

  @Test
  void calculateUpstream_twoServersEqualWeights() {
    Server server1 = new Server("http://upstream1.com", 1.0);
    Server server2 = new Server("http://upstream2.com", 1.0);
    List<Server> servers = Arrays.asList(server1, server2);

    Map<String, Integer> distribution = new HashMap<>();
    int iterations = 1000;

    for (int i = 0; i < iterations; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertNotNull(upstream);
      assertTrue(
          upstream.equals("http://upstream1.com") || upstream.equals("http://upstream2.com"));
      distribution.merge(upstream, 1, Integer::sum);
    }

    assertTrue(distribution.size() == 2, "Both upstreams should be selected at least once");
    assertTrue(
        distribution.get("http://upstream1.com") > 300, "Server1 should be selected roughly 50%");
    assertTrue(
        distribution.get("http://upstream2.com") > 300, "Server2 should be selected roughly 50%");
  }

  @Test
  void calculateUpstream_twoServersDifferentWeights() {
    Server server1 = new Server("http://upstream1.com", 3.0);
    Server server2 = new Server("http://upstream2.com", 1.0);
    List<Server> servers = Arrays.asList(server1, server2);

    Map<String, Integer> distribution = new HashMap<>();
    int iterations = 1000;

    for (int i = 0; i < iterations; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertNotNull(upstream);
      assertTrue(
          upstream.equals("http://upstream1.com") || upstream.equals("http://upstream2.com"));
      distribution.merge(upstream, 1, Integer::sum);
    }

    assertTrue(distribution.size() == 2, "Both upstreams should be selected at least once");
    assertTrue(
        distribution.get("http://upstream1.com") > 600,
        "Server1 with weight 3.0 should be selected roughly 75%");
    assertTrue(
        distribution.get("http://upstream2.com") < 400,
        "Server2 with weight 1.0 should be selected roughly 25%");
  }

  @Test
  void calculateUpstream_firstServerWeightZero() {
    Server server1 = new Server("http://upstream1.com", 0.0);
    Server server2 = new Server("http://upstream2.com", 1.0);
    List<Server> servers = Arrays.asList(server1, server2);

    for (int i = 0; i < 100; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertEquals(
          "http://upstream2.com",
          upstream,
          "Only server2 should be selected when server1 has weight 0");
    }
  }

  @Test
  void calculateUpstream_secondServerWeightZero() {
    Server server1 = new Server("http://upstream1.com", 1.0);
    Server server2 = new Server("http://upstream2.com", 0.0);
    List<Server> servers = Arrays.asList(server1, server2);

    for (int i = 0; i < 100; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertEquals(
          "http://upstream1.com",
          upstream,
          "Only server1 should be selected when server2 has weight 0");
    }
  }

  @Test
  void calculateUpstream_bothServersWeightZero() {
    Server server1 = new Server("http://upstream1.com", 0.0);
    Server server2 = new Server("http://upstream2.com", 0.0);
    List<Server> servers = Arrays.asList(server1, server2);

    assertThrows(
        LoadBalancingException.class,
        () -> LoadBalancingUtil.calculateUpstream(servers),
        "Should throw LoadBalancingException when all servers have weight 0");
  }

  @Test
  void calculateUpstream_oneServerWeightZeroOtherLargeWeight() {
    Server server1 = new Server("http://upstream1.com", 0.0);
    Server server2 = new Server("http://upstream2.com", 100.0);
    List<Server> servers = Arrays.asList(server1, server2);

    for (int i = 0; i < 100; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertEquals(
          "http://upstream2.com",
          upstream,
          "Only server2 should be selected when server1 has weight 0");
    }
  }

  @Test
  void calculateUpstream_verySmallWeights() {
    Server server1 = new Server("http://upstream1.com", 0.001);
    Server server2 = new Server("http://upstream2.com", 0.001);
    List<Server> servers = Arrays.asList(server1, server2);

    Map<String, Integer> distribution = new HashMap<>();
    int iterations = 1000;

    for (int i = 0; i < iterations; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertNotNull(upstream);
      assertTrue(
          upstream.equals("http://upstream1.com") || upstream.equals("http://upstream2.com"));
      distribution.merge(upstream, 1, Integer::sum);
    }

    assertTrue(
        distribution.size() == 2,
        "Both upstreams should be selected with very small equal weights");
  }

  @Test
  void calculateUpstream_largeWeights() {
    Server server1 = new Server("http://upstream1.com", 10000.0);
    Server server2 = new Server("http://upstream2.com", 10000.0);
    List<Server> servers = Arrays.asList(server1, server2);

    Map<String, Integer> distribution = new HashMap<>();
    int iterations = 1000;

    for (int i = 0; i < iterations; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertNotNull(upstream);
      assertTrue(
          upstream.equals("http://upstream1.com") || upstream.equals("http://upstream2.com"));
      distribution.merge(upstream, 1, Integer::sum);
    }

    assertTrue(
        distribution.size() == 2, "Both upstreams should be selected with large equal weights");
    assertTrue(
        distribution.get("http://upstream1.com") > 300, "Server1 should be selected roughly 50%");
    assertTrue(
        distribution.get("http://upstream2.com") > 300, "Server2 should be selected roughly 50%");
  }

  @Test
  void calculateUpstream_extremeWeightRatio() {
    Server server1 = new Server("http://upstream1.com", 0.01);
    Server server2 = new Server("http://upstream2.com", 99.99);
    List<Server> servers = Arrays.asList(server1, server2);

    Map<String, Integer> distribution = new HashMap<>();
    int iterations = 10000;

    for (int i = 0; i < iterations; i++) {
      String upstream = LoadBalancingUtil.calculateUpstream(servers);
      assertNotNull(upstream);
      assertTrue(
          upstream.equals("http://upstream1.com") || upstream.equals("http://upstream2.com"));
      distribution.merge(upstream, 1, Integer::sum);
    }

    assertTrue(
        distribution.get("http://upstream2.com") > 9900,
        "Server2 with weight 99.99 should be selected ~99.99%");
    assertTrue(
        distribution.getOrDefault("http://upstream1.com", 0) < 100,
        "Server1 with weight 0.01 should be selected ~0.01%");
  }
}
