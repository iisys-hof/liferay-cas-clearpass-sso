package de.hofuniversity.iisys.liferay.cas.servlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Proxy granting ticket callback servlet collection the parameters in a map
 * with the ticket IOU as key and the ticket itself as value.
 */
public class CASProxyServlet extends HttpServlet
{
	private static final String PGT_PARAM = "pgtId";
	private static final String PGT_IOU_PARAM = "pgtIou";
	
	//TODO: use callback?
	private static final Map<String, String> fTickets =
			new HashMap<String, String>();
	
	/**
	 * @param iou proxy granting ticket IOU to get a ticket for
	 * @return received proxy granting ticket or null
	 */
	public static final String getTicket(String iou)
	{
		return fTickets.remove(iou);
	}
	
	@Override
	public void destroy()
	{
		fTickets.clear();
	}

	@Override
	public void service(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, IOException
	{
		String pgtId = request.getParameter(PGT_PARAM);
		String pgtIou = request.getParameter(PGT_IOU_PARAM);
		
		if(pgtId != null || pgtIou != null)
		{
			fTickets.put(pgtIou, pgtId);
		}
		
		response.setStatus(200);
	}
}
