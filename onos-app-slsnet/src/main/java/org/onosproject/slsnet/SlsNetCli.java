/*
 * Copyright 2015-present Open Networking Laboratory
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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.incubator.net.routing.RouteInfo;
import org.onosproject.incubator.net.routing.RouteAdminService;
import org.onosproject.incubator.net.routing.RouteTableId;


/**
 * CLI to interact with the SLSNET application.
 */
@Command(scope = "onos", name = "slsnet",
         description = "Manages the SLSNET application")
public class SlsNetCli extends AbstractShellCommand {

    protected static SlsNetService slsnet;
    protected static RouteAdminService routeService;

    @Argument(index = 0, name = "command", description = "Command name: show, intents",
              required = true, multiValued = false)
    String command = null;

    @Override
    protected void execute() {
        if (slsnet == null) {
            slsnet = get(SlsNetService.class);
        }
        if (routeService == null) {
            routeService = get(RouteAdminService.class);
        }

        if (command == null) {
            print("command not found", command);
            return;
        }
        switch (command) {
        case "show":
            show();
            break;
        case "intents":
            slsnet.dump("intents");
            break;
        default:
            print("unknown command: {}", command);
            break;
        }
    }

    // Shows configuraions
    protected void show() {
        print("SlsNetAppId:\n");
        print("   %s\n", slsnet.getAppId());

        print("l2Networks:\n");
        for (L2Network l2Network : slsnet.getL2Networks()) {
            print("   %s\n", l2Network);
        }

        print("ip4Subnets:\n");
        for (IpSubnet ipSubnet : slsnet.getIp4Subnets()) {
            print("    %s\n", ipSubnet);
        }

        print("borderRoutes:\n");
        // do not show slsnet.getBorderRoutes() directly
        for (RouteTableId routeTableId : routeService.getRouteTables()) {
            if (routeTableId.name() == "ipv4") {
                for (RouteInfo routeInfo : routeService.getRoutes(routeTableId)) {
                    print("    %s %s\n", routeInfo.prefix(), routeInfo.allRoutes().toString());
                }
            }
        }

        print("virtualGatewayMacAddress:\n");
        print("    %s\n", slsnet.getVirtualGatewayMacAddress());

        print("virtualGatewayIpAddressed:\n");
        print("    %s\n", slsnet.getVirtualGatewayIpAddresses());
    }

}
