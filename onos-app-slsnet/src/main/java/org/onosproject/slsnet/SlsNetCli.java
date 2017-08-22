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

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.onosproject.cli.AbstractShellCommand;

/**
 * CLI to interact with the SLSNET application.
 */
@Command(scope = "onos", name = "slsnet",
         description = "Manages the SLSNET application")
public class SlsNetCli extends AbstractShellCommand {

    protected static SlsNetService slsnet;

    @Argument(index = 0, name = "command", description = "Command name: show, intents, refresh",
              required = true, multiValued = false)
    String command = null;

    @Override
    protected void execute() {
        if (slsnet == null) {
            slsnet = get(SlsNetService.class);
        }
        if (command == null) {
            print("command not found", command);
            return;
        }
        switch (command) {
        case "show":
            slsnet.dumpToStream("show", System.out);
            break;
        case "intents":
            slsnet.dumpToStream("intents", System.out);
            break;
        case "refresh":
            slsnet.triggerRefresh();
            print("slsnet refresh triggered\n");
            break;
        default:
            print("unknown command: {}", command);
            break;
        }
    }

}

