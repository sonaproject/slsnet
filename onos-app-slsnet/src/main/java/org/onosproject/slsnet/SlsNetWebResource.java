/*
 * Copyright 2015-present Open Networking Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.onosproject.slsnet;

import org.onosproject.rest.AbstractWebResource;

import java.io.ByteArrayOutputStream;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

/**
 * Manage SLSNET Status.
 */
@Path("")
public class SlsNetWebResource extends AbstractWebResource {

    /**
     * SLSNET Show Status; dummy for now.
     *
     * @return 200 OK
     */
    @GET
    @Path("status")
    public Response queryStatus() {
        return Response.ok("ok").build();
    }

    /**
     * SLSNET Show Configurations.
     *
     * @return 200 OK
     */
    @GET
    @Path("show")
    public Response queryShow() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        get(SlsNetService.class).dumpToStream("show", outputStream);
        return Response.ok(outputStream.toString()).build();
    }

    /**
     * SLSNET Intents Infos.
     *
     * @return 200 OK
     */
    @GET
    @Path("intents")
    public Response queryIntents() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        get(SlsNetService.class).dumpToStream("intents", outputStream);
        return Response.ok(outputStream.toString()).build();
    }

    /**
     * Trigger SlsNet Service Refresh.
     *
     * @return 200 OK
     */
    @GET
    @Path("refresh")
    public Response triggerRefresh() {
        get(SlsNetService.class).triggerRefresh();
        return Response.ok("slsnet refresh triggered").build();
    }

}

