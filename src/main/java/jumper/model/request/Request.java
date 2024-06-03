// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class Request {

  private String finalApiUrl;
  private String consumer;
  private String basePath;
  private String requestPath;
  private String method;
}
