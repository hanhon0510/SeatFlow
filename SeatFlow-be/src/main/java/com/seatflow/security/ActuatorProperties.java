package com.seatflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param metricsPublic set only where the management surface is genuinely unreachable from
 *                      outside - an internal-only port, or a cluster-local scrape. It defaults
 *                      to false so a plain deployment does not publish its business metrics.
 */
@ConfigurationProperties(prefix = "seatflow.actuator")
public record ActuatorProperties(boolean metricsPublic) {
}
