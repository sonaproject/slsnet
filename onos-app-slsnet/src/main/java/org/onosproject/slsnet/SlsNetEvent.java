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

import org.onosproject.event.AbstractEvent;

/**
 * Describes an interface event.
 */
public class SlsNetEvent extends AbstractEvent<SlsNetEvent.Type, String> {

    public enum Type {
        SLSNET_UPDATED,  // Indicates an slsnet has been updated.
        SLSNET_IDLE,     // Indicates an slsnet idle loop
        SLSNET_DUMP      // request to dump internal info on the subject
    }

    /**
     * Creates an interface event with type and subject.
     *
     * @param type event type
     * @param subject dummy subject interface
     */
    public SlsNetEvent(Type type, String subject) {
        super(type, subject);   /* subject is dummy */
    }
}

