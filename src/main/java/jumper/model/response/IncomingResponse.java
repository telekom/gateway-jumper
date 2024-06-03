// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.response;

import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncomingResponse {

  private String host;
  private Integer httpStatusCode;
  private String method;
  Map<String, String> requestHeaders;
}
