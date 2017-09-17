/*******************************************************************************
 * Copyright 2016-2017 Dell Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 *
 * @microservice: support-scheduler
 * @author: Marc Hammons, Dell
 * @version: 1.0.0
 *******************************************************************************/

package org.edgexfoundry.scheduling;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import org.edgexfoundry.domain.meta.Addressable;
import org.edgexfoundry.domain.meta.ScheduleEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ScheduleEventHTTPExecutor {

  private static final org.edgexfoundry.support.logging.client.EdgeXLogger logger =
      org.edgexfoundry.support.logging.client.EdgeXLoggerFactory
          .getEdgeXLogger(ScheduleEventHTTPExecutor.class);

  private static final String POST = "POST";

  @Value("${server.timeout}")
  private int timeout = 5000;

  @Async
  public void execute(final ScheduleEvent event) {
    int returnCode;
    String body = event.getParameters();
    if (body == null)
      body = "";
    final String url = getURL(event.getAddressable());
    try {
      if (url == null) {
        logger.info("no address for schedule event " + event.getName());
      } else {
        URL obj = new URL(url);
        HttpURLConnection con = (HttpURLConnection) obj.openConnection();
        // per original intent, defaulting to POST.
        con.setRequestMethod(POST);
        con.setDoOutput(true);
        con.setConnectTimeout(timeout);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Content-Length", Integer.toString(body.length()));
        OutputStream os = con.getOutputStream();
        os.write(body.getBytes());
        returnCode = con.getResponseCode();
        logger.info("executed event " + event.getId() + " '" + event.getName() + "' response code "
            + returnCode + " url '" + url + "' body '" + body + "'");
      }
    } catch (Exception e) {
      logger.error("exception executing event " + event.getId() + " '" + event.getName() + "' url '"
          + url + "' body '" + body + "' exception " + e.getMessage());
    }
  }

  private String getURL(Addressable addressable) {
    if (addressable != null) {
      StringBuilder builder = new StringBuilder(addressable.getProtocol().toString());
      builder.append("://");
      builder.append(addressable.getAddress());
      builder.append(":");
      builder.append(addressable.getPort());
      builder.append(addressable.getPath());
      return builder.toString();
    }
    return null;
  }
}
