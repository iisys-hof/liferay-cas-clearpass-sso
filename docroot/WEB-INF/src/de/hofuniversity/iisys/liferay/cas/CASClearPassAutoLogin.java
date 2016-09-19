package de.hofuniversity.iisys.liferay.cas;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.ResourceBundle;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.AutoLogin;
import com.liferay.portal.security.auth.BaseAutoLogin;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;

import de.hofuniversity.iisys.liferay.cas.servlet.CASProxyServlet;

/**
 * CAS autologin hook using ClearPass to retrieve and temporarily store the
 * user's credentials despite using a full CAS login. Proxy granting tickets
 * are received using the CASProxyServlet. After successful authentication
 * users are redirected to the page they originally tried to visit
 * 
 * Has an opt-out option triggered by supplying the parameter "casOptOut=true".
 */
public class CASClearPassAutoLogin extends BaseAutoLogin
{
	private static Log _log = LogFactoryUtil
			.getLog(CASClearPassAutoLogin.class);
	
	private static final String PROPERTIES = "cas_autologin";

	private static final String CAS_URL_PROP = "autologin.cas.cas_url";
	private static final String CLEARPASS_URL_PROP = "autologin.cas.clearpass_url";
	private static final String LIFERAY_LOGIN_PROP = "autologin.cas.liferay_login_url";
	private static final String PGT_CALLBACK_PROP = "autologin.cas.pgt_callback_url";
	private static final String DEBUG_LOG_PROP = "autologin.cas.debug_logging";
	
	private static final String CAS_USER_ATT = "CAS_USER";
	private static final String CAS_TICKET_ATT = "CAS_TICKET";
	private static final String CAS_OPTOUT_ATT = "CAS_OPTOUT";
	private static final String ORIGINAL_URL_ATT = "ORIGINAL_URL";
	
	private static final String TICKET_PARAM = "ticket";
	private static final String PGT_URL_PARAM = "pgtUrl";
	private static final String TARGET_PARAM = "targetService";
	
	private static final String OPT_OUT_PARAM = "casOptOut";
	private static final String CAS_LOGOUT_PARAM = "casLogout";
	
	private static final String TICKET_VAL_FRAG = "serviceValidate?service=";
	private static final String LOGIN_FRAG = "login?service=";
	private static final String LOGOUT_FRAG = "logout";
	private static final String PROXY_PGT_FRAG = "proxy?pgt=";
	
	private static final String CAS_USER_TAG = "<cas:user>";
	private static final String CAS_USER_END_TAG = "</cas:user>";
	private static final String CAS_PGT_TAG = "<cas:proxyGrantingTicket>";
	private static final String CAS_PGT_END_TAG = "</cas:proxyGrantingTicket>";
	private static final String CAS_PROXY_TICKET_TAG = "<cas:proxyTicket>";
	private static final String CAS_PROXY_TICKET_END_TAG = "</cas:proxyTicket>";
	private static final String CAS_CREDS_TAG = "<cas:credentials>";
	private static final String CAS_CREDS_END_TAG = "</cas:credentials>";
	
	private final String fCasUrl;
	private final String fCasClearPassUrl;
	private final String fLoginUrl;
	private final String fPgtCallback;
	
	private final boolean fDebug;
	
	/**
	 * Creates a CAS clearpass autologin based on the included properties file.
	 */
	public CASClearPassAutoLogin()
	{
		//read configuration from properties file
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
        ResourceBundle rb = ResourceBundle.getBundle(PROPERTIES,
            Locale.getDefault(), loader);
        
        fCasUrl = rb.getString(CAS_URL_PROP);
        fCasClearPassUrl = rb.getString(CLEARPASS_URL_PROP);
        fLoginUrl = rb.getString(LIFERAY_LOGIN_PROP);
        fPgtCallback = rb.getString(PGT_CALLBACK_PROP);
        
        fDebug = Boolean.parseBoolean(rb.getString(DEBUG_LOG_PROP));
	}
	
	@Override
	protected String[] doLogin(
			HttpServletRequest request, HttpServletResponse response)
		throws Exception
	{
		//this method is only called for unauthenticated users
		HttpSession session = request.getSession();
		
		if(fDebug)
		{
			_log.info("ClearPassAutoLogin executed");
		}
		
		String[] credentials = null;
		
		try
		{
			boolean handled = false;
			
			//check whether user wants to opt-out
			handled = handleOptOut(request, response, session);
			
			//check whether user wants to log out
			handled = (handled || handleLogout(request, response, session));
			
			//handle clearPass login process otherwise
			if(!handled)
			{
				credentials = retrieveCredentials(request, response, session);
			}
		}
		catch(Exception e)
		{
			_log.warn("ClearPassHook Exception: ", e);
		}
		
		return credentials;
	}
	
	private String[] retrieveCredentials(HttpServletRequest request,
			HttpServletResponse response, HttpSession session) throws Exception
	{
		Long remoteUserId = null;
		String ticket = null;
		String tgt = null;
		String password = null;

		long companyId = PortalUtil.getCompanyId(request);
		
		if(fDebug)
		{
			_log.info("\tcompanyId: " + companyId);
		}

		//user already logged in with CAS?
		remoteUserId = (Long) session.getAttribute(CAS_USER_ATT);

		if(fDebug)
		{
			_log.info("\tremoteUserId: " + remoteUserId);
		}
		
		//if no user is logged in, determine which step of the authentication
		//we are at
		String[] replyData = null;
		if(remoteUserId == null)
		{
			//replyData will contain a userId and a ticket granting ticket IOU
			
			//get ticket from request for clearpass-request
			String[] tickets = request.getParameterValues(TICKET_PARAM);
			if(tickets != null && tickets.length > 0)
			{
				ticket = tickets[0];
				session.setAttribute(CAS_TICKET_ATT, ticket);

				if(fDebug)
				{
					_log.info("\tgot ticket: " + ticket);
				}
				
				//try verifying received ticket
				replyData = handleLogin(ticket, companyId);
			}
			else if(session.getAttribute(CAS_TICKET_ATT) != null)
			{
				//extra ticket-fallback if Liferay stacks redirects that would cause an infinite loop
				ticket = (String) session.getAttribute(CAS_TICKET_ATT);

				if(fDebug)
				{
					_log.info("\tsession ticket: " + ticket);
				}
				
				//try verifying stored ticket
				replyData = handleLogin(ticket, companyId);
			}
			
			//response from ticket verification
			if(replyData != null)
			{
				if(replyData[0] != null)
				{
					remoteUserId = Long.parseLong(replyData[0]);
				}
				
				//user IOU to get ticket
				if(fDebug)
				{
					_log.info("\tusing tgt iou: " + replyData[1]);
				}
				tgt = CASProxyServlet.getTicket(replyData[1]);
				if(fDebug)
				{
					_log.info("\textracted tgt: " + tgt);
				}
			}

			//if no ticket has been received yet, trigger the CAS login sequence
			if(ticket == null)
			{
				if(fDebug)
				{
					_log.info("\tno ticket");
				}
				
				//store current URL
				session.setAttribute(ORIGINAL_URL_ATT, getRequestUrl(request));
				
				//send redirect to CAS login
				String url = fCasUrl + LOGIN_FRAG + fLoginUrl;
				request.setAttribute(AutoLogin.AUTO_LOGIN_REDIRECT, url);
			}

			//user clearpass using the ticket granting ticket
			if(tgt != null)
			{
				 password = retrievePassword(tgt);
			}
			
			//collect credentials for lieray API
			if(remoteUserId != null)
			{
				//construct credentials array
				replyData = getCredentials(remoteUserId, password);
				
				//redirect back to original request url
				String origUrl = (String) session.getAttribute(ORIGINAL_URL_ATT);
				if(origUrl != null)
				{
					if(fDebug)
					{
						_log.info("redirecting back to: " + origUrl);
					}
					session.removeAttribute(ORIGINAL_URL_ATT);
					request.setAttribute(AutoLogin.AUTO_LOGIN_REDIRECT_AND_CONTINUE, origUrl);
				}
				
				//clear temporary variables
				session.removeAttribute(CAS_USER_ATT);
				session.removeAttribute(CAS_TICKET_ATT);
				
				return replyData;
			}
		}
		
		return null;
	}
	
	private String getRequestUrl(HttpServletRequest request)
	{
		//construct full request URL
		return request.getScheme() + "://" +
          request.getServerName() + 
            ("http".equals(request.getScheme()) && request.getServerPort() == 80
            || "https".equals(request.getScheme()) && request.getServerPort() == 443
            ? "" : ":" + request.getServerPort() ) +
          request.getRequestURI() +
            (request.getQueryString() != null
          	? "?" + request.getQueryString() : "");
	}
	
	private String[] handleLogin(String ticket, long companyId) throws Exception
	{
		Long userId = null;
		String userName = null;
		String pgtIou = null;
		
		//verify the given ticket, retrieve user name and TGT IOU
		String casUrl = fCasUrl + TICKET_VAL_FRAG
				+ fLoginUrl + "&" + TICKET_PARAM + "=" + ticket;
		
		//also get proxy ticket through callback
		casUrl += "&" + PGT_URL_PARAM + "=" + fPgtCallback;
		
		String reply = sendRequest(casUrl);
		
		if(fDebug)
		{
			_log.info("\tgot validation: " + reply);
		}
		
		boolean error = false;
		if(reply.indexOf(CAS_USER_TAG) > 0)
		{
			userName = reply.substring(reply.indexOf(CAS_USER_TAG) + CAS_USER_TAG.length(),
					reply.indexOf(CAS_USER_END_TAG));
			User user = UserLocalServiceUtil.fetchUserByScreenName(companyId, userName);
			userId = user.getUserId();
		}
		else
		{
			_log.error("failed to extract user name from CAS validation response");
			error = true;
		}
		
		if(reply.indexOf(CAS_PGT_TAG) > 0)
		{
			pgtIou = reply.substring(reply.indexOf(CAS_PGT_TAG) + CAS_PGT_TAG.length(),
					reply.indexOf(CAS_PGT_END_TAG));
		}
		else
		{
			_log.error("failed to extract TGT IOU from CAS validation response");
			error = true;
		}
		
		if(error)
		{
			throw new Exception("failed to process CAS validation response:\n" + reply);
		}
		else
		{
			return new String[] {Long.toString(userId), pgtIou};
		}
	}
	
	private String[] getCredentials(long userId, String password)
	{
		if(fDebug)
		{
			User user = UserLocalServiceUtil.fetchUser(userId);
			_log.info("\tuser: " + user.getScreenName());
		}
		
		String[] credentials = new String[3];

		credentials[0] = String.valueOf(userId);
		credentials[1] = password;
		
		//no password validation
		credentials[2] = Boolean.FALSE.toString();

		if(fDebug)
		{
			_log.info("\treturning: " + credentials[0]
					+ " - " + credentials[1] + " - " + credentials[2]);
		}
		
		return credentials;
	}
	
	private String retrievePassword(String tgt) throws Exception
	{
		String password = null;
		String proxyTicket = null;
		
		//retrieve proxy ticket for CAS' clearpass service
		//(proxied authentication in the name of the user)
		String casUrl = fCasUrl + PROXY_PGT_FRAG + tgt
				+ "&" + TARGET_PARAM + "=" + fCasClearPassUrl;
		
		String response = sendRequest(casUrl);
		
		if(fDebug)
		{
			_log.info("\tproxy ticket response: " + response);
		}
		
		if(response.indexOf(CAS_PROXY_TICKET_TAG) > 0)
		{
			proxyTicket = response.substring(response.indexOf(CAS_PROXY_TICKET_TAG)
					+ CAS_PROXY_TICKET_TAG.length(), response.indexOf(CAS_PROXY_TICKET_END_TAG));
		}
		else
		{
			_log.error("failed to retrieve proxy ticket from CAS response: " + response);
		}
		
		if(proxyTicket != null)
		{
			//retrieve clearpass credentials using proxy ticket
			casUrl = fCasClearPassUrl + "?" + TICKET_PARAM + "=" + proxyTicket;
			
			try
			{
				response = sendRequest(casUrl);
				
				if(fDebug)
				{
					_log.info("\tclearpass response: " + response);
				}
				
				if(response.indexOf(CAS_CREDS_TAG) > 0)
				{
					password = response.substring(response.indexOf(CAS_CREDS_TAG)
						+ CAS_CREDS_TAG.length(), response.indexOf(CAS_CREDS_END_TAG));
				}
				else
				{
					_log.error("failed to retrieve password from CAS response: " + response);
				}
			}
			catch(Exception e)
			{
				_log.error(e);
			}
		}
		
		if(password == null)
		{
			_log.error("failed to retrieve password, continuing without it");
		}
		
		return password;
	}
	
	private boolean handleOptOut(HttpServletRequest request,
			HttpServletResponse response, HttpSession session) throws Exception
	{
		boolean optOut = false;
		
		//get existing opt-out flag from session
		Boolean optOutAtt = (Boolean) session.getAttribute(CAS_OPTOUT_ATT);
		if(optOutAtt != null)
		{
			optOut = optOutAtt;
		}
		
		//get flag override from parameters
		String[] optOutVals = request.getParameterValues(OPT_OUT_PARAM);
		if(optOutVals != null && optOutVals.length > 0)
		{
			optOut = Boolean.parseBoolean(optOutVals[0]);
		}
		
		//set or clear flag
		if(optOut)
		{
			if(fDebug)
			{
				_log.info("setting autologin opt-out flag");
			}
			
			session.setAttribute(CAS_OPTOUT_ATT, optOut);
		}
		else
		{
			session.removeAttribute(CAS_OPTOUT_ATT);
		}
		
		return optOut;
	}
	
	private boolean handleLogout(HttpServletRequest request,
			HttpServletResponse response, HttpSession session) throws Exception
	{
		boolean logout = false;
		
		String[] logoutVals = request.getParameterValues(CAS_LOGOUT_PARAM);
		if(logoutVals != null && logoutVals.length > 0)
		{
			logout = Boolean.parseBoolean(logoutVals[0]);
		}
		
		if(logout)
		{
			if(fDebug)
			{
				_log.info("logout: clearing autologin session attributes");
			}
			
			//clear own variables
			session.removeAttribute(CAS_USER_ATT);
			session.removeAttribute(CAS_TICKET_ATT);
			
			//TODO: also clear opt-out flag?
			
			//redirect to CAS logout
			String url = fCasUrl + LOGOUT_FRAG;
			request.setAttribute(AutoLogin.AUTO_LOGIN_REDIRECT, url);
		}
		
		return logout;
	}
	
	private String sendRequest(String url) throws Exception
	{
		String reply = null;
		
		URL reqUrl = new URL(url);

        final HttpURLConnection connection =
            (HttpURLConnection) reqUrl.openConnection();

        connection.setRequestMethod("GET");
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getInputStream()));
        final StringBuffer resBuff = new StringBuffer();
        String line = reader.readLine();
        while(line != null)
        {
        	resBuff.append(line);
        	resBuff.append("\r\n");
            line = reader.readLine();
        }
		reply = resBuff.toString();
        reader.close();
        
		return reply;
	}
}
