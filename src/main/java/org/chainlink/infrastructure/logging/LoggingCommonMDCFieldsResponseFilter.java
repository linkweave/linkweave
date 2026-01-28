package org.chainlink.infrastructure.logging;

import ch.dvbern.dvbstarter.FilterPriorities;
import ch.dvbern.oss.commons.logging.mdc.jaxrsfilter.CommonMDCFieldResponseFilterHelper;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;
import lombok.NoArgsConstructor;

@Provider
@PreMatching
@Priority(FilterPriorities.LOGGING_INIT)
@RequestScoped
@NoArgsConstructor
public class LoggingCommonMDCFieldsResponseFilter implements ContainerResponseFilter {

    private final CommonMDCFieldResponseFilterHelper helper = CommonMDCFieldResponseFilterHelper.usingDefaults();

    @Override
    public void filter(
        ContainerRequestContext requestContext,
        ContainerResponseContext responseContext
    ) {
        helper.filter(requestContext, responseContext);
    }

}
