package io.quarkus.vertx.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.vertx.core.http.HttpServerRequest;

/**
 * Identifies a route method parameter that should be injected with a value returned from
 * {@link HttpServerRequest#getHeader(String)}.
 * <p>
 * The parameter type must be {@link String} or {@code Optional<String>}, otherwise the build fails.
 * 
 * @see HttpServerRequest#getHeader(String)
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Header {

    /**
     * Constant value for {@link #value()} indicating that the annotated element's name should be used as-is.
     */
    String ELEMENT_NAME = "<<element name>>";

    /**
     * The name of the header. By default, the element's name is used as-is.
     *
     * @return the name of the header
     */
    String value() default ELEMENT_NAME;

}