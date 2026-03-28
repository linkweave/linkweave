package ch.dvbern.dvbstarter.types.url;

import java.net.URL;

import ch.dvbern.dvbstarter.openapi.OpenApiConst;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * This class is only used to define the format of an {@link URL} for OpenAPI.
 * <p/>
 * Intended use: <br/>
 * <pre>
 * {@code
 * @Schema(required = true, implementation = URLSchema.class)
 * private final URL someURL;
 * }
 * </pre>
 */
@Schema(type = SchemaType.STRING, format = OpenApiConst.Format.URL)
public interface URLSchema {
}
