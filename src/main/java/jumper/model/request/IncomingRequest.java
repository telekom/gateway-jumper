package jumper.model.request;

import java.util.HashMap;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncomingRequest extends Request {
  HashMap<String, String> logEntries;
}
