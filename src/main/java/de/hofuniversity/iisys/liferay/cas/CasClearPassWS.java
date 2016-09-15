package de.hofuniversity.iisys.liferay.cas;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;

import org.osgi.service.component.annotations.Component;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;

@Component(immediate = true, property = {"jaxrs.application=true"}, service = Application.class)
@ApplicationPath("/CASClearPass")
public class CasClearPassWS extends Application
{
	private static Log _log = LogFactoryUtil
			.getLog(CasClearPassWS.class);

	private static final String PROPERTIES = "cas_autologin";

	private static final String DEBUG_LOG_PROP = "autologin.cas.debug_logging";
	
	//TODO: use callback?
	private static final Map<String, String> fTickets =
			new HashMap<String, String>();

	private final boolean fDebug;
	
	/**
	 * @param iou proxy granting ticket IOU to get a ticket for
	 * @return received proxy granting ticket or null
	 */
	public static String getTicket(String iou)
	{
		return fTickets.remove(iou);
	}
	
	public CasClearPassWS()
	{
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
        ResourceBundle rb = ResourceBundle.getBundle(PROPERTIES,
            Locale.getDefault(), loader);
 
        fDebug = Boolean.parseBoolean(rb.getString(DEBUG_LOG_PROP));
	}
	
	public Set<Object> getSingletons() {
        return Collections.<Object>singleton(this);
    }
	
	// web service method
	@GET
    @Path("/pgtCallback")
    @Produces("text/text")
	public String CasCallbackWS(
		@DefaultValue("0") @QueryParam("pgtId") String pgtId,
		@DefaultValue("nope") @QueryParam("pgtIou") String pgtIou)
	{
		if(fDebug)
		{
			_log.info("extracted pgtId: " + pgtId);
			_log.info("extracted pgtIou: " + pgtIou);
		}
		
		if(pgtId != null || pgtIou != null)
		{
			fTickets.put(pgtIou, pgtId);
		}
		
		return "OK";
	}
}