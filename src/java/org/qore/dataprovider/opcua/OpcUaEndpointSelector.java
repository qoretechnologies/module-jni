/*  OpcUaEndpointSelector.java Copyright 2026 Qore Technologies, s.r.o.

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
    associated documentation files (the "Software"), to deal in the Software without restriction.
    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
*/

package org.qore.dataprovider.opcua;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.structured.EndpointDescription;

/** Endpoint selector helpers for Milo clients. */
public final class OpcUaEndpointSelector {
    private OpcUaEndpointSelector() {
    }

    /**
     * Returns an endpoint selector matching the requested security policy.
     *
     * @param securityPolicyName Milo {@link SecurityPolicy} enum name; empty/null means {@code None}
     * @return selector returning the first matching endpoint, or the first endpoint as fallback
     */
    public static Function<List<EndpointDescription>, Optional<EndpointDescription>>
            forSecurityPolicy(String securityPolicyName) {
        SecurityPolicy securityPolicy = SecurityPolicy.valueOf(
            securityPolicyName == null || securityPolicyName.isEmpty() ? "None" : securityPolicyName);
        String securityPolicyUri = securityPolicy.getUri();

        return endpoints -> {
            if (endpoints == null || endpoints.isEmpty()) {
                return Optional.empty();
            }

            for (EndpointDescription endpoint : endpoints) {
                if (securityPolicyUri.equals(endpoint.getSecurityPolicyUri())) {
                    return Optional.of(endpoint);
                }
            }

            return Optional.of(endpoints.get(0));
        };
    }
}
