package jumper.model.config;

import lombok.Data;

@Data
public class GatewayClient {
  private String id;
  private String secret;
  private String issuer;
}
