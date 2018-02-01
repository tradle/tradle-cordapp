package com.template;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.template.schema.SharedItemSchemaV1;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.core.schemas.MappedSchema;
import net.corda.webserver.services.WebServerPluginRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class TemplateWebPlugin implements WebServerPluginRegistry {
    /**
     * A list of classes that expose web APIs.
     */
    @NotNull
    @Override
    public List<Function<CordaRPCOps, ?>> getWebApis() {
        return ImmutableList.of(SharedItemApi::new);
    }

    /**
     * A list of directories in the resources directory that will be served by Jetty under /web.
     * The template's web frontend is accessible at /web/template.
     */
    @NotNull
    @Override
    public Map<String, String> getStaticServeDirs() {
        return ImmutableMap.of(
                // This will serve the templateWeb directory in resources to /web/template
                "template", getClass().getClassLoader().getResource("templateWeb").toExternalForm());
    }

    @Override
    public void customizeJSONSerialization(ObjectMapper objectMapper) {

    }

//    @NotNull
//    @Override
//    public Set<MappedSchema> getRequiredSchemas()
//    {
//        Set<MappedSchema> requiredSchemas = new HashSet<>();
//        requiredSchemas.add(new SharedItemSchemaV1());
//        return requiredSchemas;
//    }
}