package ch.dvbern.dvbstarter.types.semver;

import ch.dvbern.dvbstarter.openapi.OpenApiConst;
import com.vdurmont.semver4j.Semver;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * This class is only used to define the format of an {@link Semver} for OpenAPI.
 * <p/>
 * Intended use: <br/>
 * <pre>
 * {@code
 * @Schema(required = true, implementation = SemverSchema.class)
 * private final Semver appVersion;
 * }
 * </pre>
 */
@Schema(type = SchemaType.STRING, format = OpenApiConst.Format.SEMANTIC_VERSION)
public interface SemverSchema {
}
