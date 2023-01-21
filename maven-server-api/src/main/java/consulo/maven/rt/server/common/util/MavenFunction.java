package consulo.maven.rt.server.common.util;

/**
 * @author VISTALL
 * @since 2019-03-07
 */
public interface MavenFunction<PARAM, RESULT>
{
	RESULT invoke(PARAM param);
}
