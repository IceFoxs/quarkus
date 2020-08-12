package io.quarkus.qrs.runtime.handlers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.quarkus.arc.Arc;
import io.quarkus.qrs.runtime.core.QrsRequestContext;
import io.quarkus.qrs.runtime.mapping.RequestMapper;
import io.quarkus.qrs.runtime.mapping.RuntimeResource;
import io.quarkus.qrs.runtime.spi.BeanFactory;

public class ResourceLocatorHandler implements RestHandler {

    private final Map<Class<?>, Map<String, RequestMapper<RuntimeResource>>> resourceLocatorHandlers = new ConcurrentHashMap<>();

    @Override
    public void handle(QrsRequestContext requestContext) throws Exception {
        Object locator = requestContext.getResult();
        Class<?> locatorClass;
        if (locator instanceof Class) {
            locatorClass = (Class<?>) locator;
            try {
                locator = Arc.container().instance(locatorClass).get();
            } catch (Exception e) {
                requestContext.setThrowable(
                        new RuntimeException("Could not instantiate resource bean " + locatorClass
                                + " make sure it has a bean defining annotation", e));
                return;
            }
        } else {
            locatorClass = locator.getClass();
        }
        Map<String, RequestMapper<RuntimeResource>> target = findTarget(locatorClass);
        if (target == null) {
            requestContext.setThrowable(
                    new RuntimeException("Resource locator method returned object that was not a resource: " + locator));
            return;
        }
        RequestMapper<RuntimeResource> mapper = target.get(requestContext.getMethod());
        if (mapper == null) {
            requestContext.setThrowable(
                    new WebApplicationException(Response.status(HttpResponseStatus.NOT_FOUND.code()).build()));
            return;
        }
        RequestMapper.RequestMatch<RuntimeResource> res = mapper
                .map(requestContext.getRemaining().isEmpty() ? "/" : requestContext.getRemaining());
        if (res == null) {
            requestContext.setThrowable(
                    new WebApplicationException(Response.status(HttpResponseStatus.NOT_FOUND.code()).build()));
            return;
        }
        requestContext.saveUriMatchState();
        requestContext.setRemaining(res.remaining);
        requestContext.setPathParamValues(res.pathParamValues);
        requestContext.setEndpointInstance(new FixedBeanInstance(locator));
        requestContext.setResult(null);
        requestContext.restart(res.value);

    }

    private Map<String, RequestMapper<RuntimeResource>> findTarget(Class<?> locatorClass) {
        if (locatorClass == Object.class || locatorClass == null) {
            return null;
        }
        Map<String, RequestMapper<RuntimeResource>> res = resourceLocatorHandlers.get(locatorClass);
        if (res != null) {
            return res;
        }
        //not found, so we need to compute one
        //we look through all interfaces and superclasses
        //we need to do this as it could implement multiple interfaces
        List<Map<String, RequestMapper<RuntimeResource>>> results = new ArrayList<>();
        Set<Class<?>> seen = new HashSet<>();
        findTargetRecursive(locatorClass, results, seen);
        Map<String, List<RequestMapper.RequestPath<RuntimeResource>>> newMapper = new HashMap<>();
        for (Map<String, RequestMapper<RuntimeResource>> i : results) {
            for (Map.Entry<String, RequestMapper<RuntimeResource>> entry : i.entrySet()) {
                List<RequestMapper.RequestPath<RuntimeResource>> list = newMapper.get(entry.getKey());
                if (list == null) {
                    newMapper.put(entry.getKey(), list = new ArrayList<>());
                }
                list.addAll(entry.getValue().getTemplates());
            }
        }
        Map<String, RequestMapper<RuntimeResource>> finalResult = new HashMap<>();
        for (Map.Entry<String, List<RequestMapper.RequestPath<RuntimeResource>>> i : newMapper.entrySet()) {
            finalResult.put(i.getKey(), new RequestMapper<RuntimeResource>(i.getValue()));
        }
        //it does not matter if this is computed twice
        resourceLocatorHandlers.put(locatorClass, finalResult);
        return finalResult;
    }

    private void findTargetRecursive(Class<?> locatorClass, List<Map<String, RequestMapper<RuntimeResource>>> found,
            Set<Class<?>> seen) {
        if (locatorClass == Object.class || locatorClass == null) {
            return;
        }
        boolean superRequired = true;
        Map<String, RequestMapper<RuntimeResource>> res = resourceLocatorHandlers.get(locatorClass);
        if (res != null) {
            found.add(res);
            superRequired = false;
        }
        for (Class<?> iface : locatorClass.getInterfaces()) {
            if (seen.contains(iface)) {
                continue;
            }
            seen.add(iface);
            res = resourceLocatorHandlers.get(iface);
            if (res != null) {
                found.add(res);
            }
            for (Class<?> i : iface.getInterfaces()) {
                findTargetRecursive(i, found, seen);
            }
        }
        if (superRequired) {
            findTargetRecursive(locatorClass.getSuperclass(), found, seen);
        }
    }

    public void addResource(Class<?> resourceClass, Map<String, RequestMapper<RuntimeResource>> requestMapper) {
        Class<?> c = resourceClass;
        resourceLocatorHandlers.put(c, requestMapper);

    }

    private static class FixedBeanInstance implements BeanFactory.BeanInstance<Object> {
        private final Object locator;

        public FixedBeanInstance(Object locator) {
            this.locator = locator;
        }

        @Override
        public Object getInstance() {
            return locator;
        }

        @Override
        public void close() {

        }
    }
}