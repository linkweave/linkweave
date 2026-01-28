/*
 * Copyright (C) 2022 DV Bern AG, Switzerland
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.dvbern.dvbstarter.rest;

import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.MDC;

/**
 * Extract content of X-Forwarded-For header in case we want to use it in logging output
 */
@Provider
@PreMatching
@RequestScoped
public class HttpForwardedForHeaderFilter implements ContainerRequestFilter {

    public static final String LOG_MDC_FORWARDED_FOR = "forwardedFor";



    @Override
    public void filter(ContainerRequestContext context) {
        var forwardedForFilter = context.getHeaderString("X-Forwarded-For");
        String ip = forwardedForFilter != null ? forwardedForFilter : "unknown";
        MDC.put(LOG_MDC_FORWARDED_FOR, ip); // add to log context
    }
}
