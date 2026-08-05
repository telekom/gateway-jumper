// SPDX-FileCopyrightText: 2026 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.mocks;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * A minimal HTTP upstream that writes a caller-supplied response byte-for-byte onto the socket.
 *
 * <p>Unlike WireMock/Jetty (or any conformant HTTP server), this mock performs <b>no</b> framing
 * normalisation: it never reconciles {@code Content-Length} against {@code Transfer-Encoding},
 * never rewrites headers, and never validates the response. That makes it the only reliable way to
 * put deliberately malformed or ambiguous responses (e.g. conflicting {@code Transfer-Encoding} +
 * {@code Content-Length} framing, used for request-smuggling tests) on the wire so a client decoder
 * actually has to deal with them.
 *
 * <p>The same response is served to every connection until {@link #close()} is called. Typical use:
 *
 * <pre>{@code
 * try (RawHttpUpstream upstream = RawHttpUpstream.serving(response).start()) {
 *   String url = upstream.baseUrl();
 *   // ... point the client under test at url ...
 * }
 * }</pre>
 */
public class RawHttpUpstream implements AutoCloseable {

  private final byte[] rawResponse;
  private ServerSocket serverSocket;
  private Thread acceptThread;
  private volatile boolean running;

  private RawHttpUpstream(byte[] rawResponse) {
    this.rawResponse = rawResponse;
  }

  /**
   * Creates an upstream that will emit {@code rawResponse} verbatim (encoded as ISO-8859-1, i.e.
   * one byte per char, so status line, headers and body are sent exactly as written).
   */
  public static RawHttpUpstream serving(String rawResponse) {
    return new RawHttpUpstream(rawResponse.getBytes(StandardCharsets.ISO_8859_1));
  }

  /**
   * Builds an HTTP/1.1 200 response carrying <b>both</b> {@code Transfer-Encoding: chunked} and
   * {@code Content-Length}, which is the malformed framing forbidden by RFC 9112 §6.1 and rejected
   * by strict decoders (Netty 4.2 with {@code useRfc9112TransferEncoding=true}).
   */
  public static RawHttpUpstream servingConflictingFramingHeaders(String jsonBody) {
    String chunkedBody =
        Integer.toHexString(jsonBody.getBytes(StandardCharsets.UTF_8).length)
            + "\r\n"
            + jsonBody
            + "\r\n0\r\n\r\n";
    String response =
        "HTTP/1.1 200 OK\r\n"
            + "Content-Type: application/json\r\n"
            + "Transfer-Encoding: chunked\r\n"
            + "Content-Length: "
            + jsonBody.getBytes(StandardCharsets.UTF_8).length
            + "\r\n"
            + "\r\n"
            + chunkedBody;
    return serving(response);
  }

  /** Binds to an ephemeral port and starts serving in a daemon thread. */
  public RawHttpUpstream start() throws IOException {
    serverSocket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress());
    running = true;
    acceptThread = new Thread(this::serveLoop, "raw-http-upstream");
    acceptThread.setDaemon(true);
    acceptThread.start();
    return this;
  }

  public int getPort() {
    return serverSocket.getLocalPort();
  }

  public String baseUrl() {
    return "http://localhost:" + getPort();
  }

  private void serveLoop() {
    while (running && !serverSocket.isClosed()) {
      try (Socket connection = serverSocket.accept()) {
        drainRequestHeaders(connection.getInputStream());
        OutputStream out = connection.getOutputStream();
        out.write(rawResponse);
        out.flush();
      } catch (IOException e) {
        // Expected on shutdown (socket closed) or when the client resets after rejecting the
        // malformed response. Nothing actionable in a test double.
      }
    }
  }

  /** Consumes the request head up to and including the terminating CRLF CRLF. */
  private static void drainRequestHeaders(InputStream in) throws IOException {
    int state = 0; // progress through \r \n \r \n
    int b;
    while ((b = in.read()) != -1) {
      switch (state) {
        case 0 -> state = (b == '\r') ? 1 : 0;
        case 1 -> state = (b == '\n') ? 2 : 0;
        case 2 -> state = (b == '\r') ? 3 : 0;
        case 3 -> {
          if (b == '\n') {
            return;
          }
          state = 0;
        }
        default -> state = 0;
      }
    }
  }

  @Override
  public void close() throws IOException {
    running = false;
    if (serverSocket != null) {
      serverSocket.close();
    }
    if (acceptThread != null) {
      acceptThread.interrupt();
    }
  }
}
