// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.response;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncomingResponse {

  private String host;
  private String path;
  private Integer httpStatusCode;

  List<String> originHeaderResponse;
}
