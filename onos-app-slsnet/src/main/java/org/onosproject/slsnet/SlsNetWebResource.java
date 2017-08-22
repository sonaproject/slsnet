package org.onosproject.slsnet;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.org.apache.regexp.internal.RE;
import org.onosproject.rest.AbstractWebResource;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Path("status")
public class SlsNetWebResource extends AbstractWebResource {
    @GET
    public Response querySlsNetStatus() {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode arrayNode = mapper.createArrayNode();
        ObjectNode node = mapper.createObjectNode();
        node.put("id", "0001");
        node.put("status", "OK");
        arrayNode.add(node);
        root.set("SlsNetStatus", arrayNode);
        return Response.ok(root.toString(), MediaType.APPLICATION_JSON_TYPE).build();
    }
}
