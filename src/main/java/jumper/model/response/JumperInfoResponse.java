// SPDX-FileCopyrightText: 2023 Deutsche Telekom AG
//
// SPDX-License-Identifier: Apache-2.0

package jumper.model.response;

import jumper.util.ObjectMapperUtil;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.core.JacksonException;

@Getter
@Setter
public class JumperInfoResponse {
  IncomingResponse incomingResponse;

  @Override
  public String toString() {
    try {
      return ObjectMapperUtil.getInstance().writeValueAsString(incomingResponse);
    } catch (JacksonException e) {
      throw new RuntimeException(e);
    }
  }
}
