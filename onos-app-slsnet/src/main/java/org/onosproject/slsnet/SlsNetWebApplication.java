package org.onosproject.slsnet;

import org.onlab.rest.AbstractWebApplication;
import org.onosproject.rest.AbstractWebResource;

import java.util.Set;

public class SlsNetWebApplication extends AbstractWebApplication {
    @Override
    public Set<Class<?>> getClasses() {
        return getClasses(SlsNetWebResource.class);
    }
}
