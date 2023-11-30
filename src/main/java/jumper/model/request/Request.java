package jumper.model.request;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Request {

  private String host;
  private String basePath;
  private String resource;
  private String method;

  private Map<String, String> originHeader;
}
