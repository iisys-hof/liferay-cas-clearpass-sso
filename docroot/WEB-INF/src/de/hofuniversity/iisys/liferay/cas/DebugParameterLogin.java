package de.hofuniversity.iisys.liferay.cas;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.liferay.portal.model.Company;
import com.liferay.portal.model.User;
import com.liferay.portal.security.auth.BaseAutoLogin;
import com.liferay.portal.service.UserLocalServiceUtil;
import com.liferay.portal.util.PortalUtil;

public class DebugParameterLogin extends BaseAutoLogin
{
	@Override
	protected String[] doLogin(HttpServletRequest request, HttpServletResponse response)
			throws Exception
	{
		String[] credentials = null;
		
		String user = null;
		String[] usernames = request.getParameterValues("user");
		if(usernames != null && usernames.length > 0)
		{
			user = usernames[0];
		}
		
		if(user != null && !user.isEmpty())
		{
			Company company = PortalUtil.getCompany(request);
			User localUser = UserLocalServiceUtil.fetchUserByScreenName(
					company.getCompanyId(), user);
			
			credentials = new String[3];
			credentials[0] = String.valueOf(localUser.getUserId());
			
			//TODO: debug password list or password parameter
//			credentials[1] = CASClearpassServlet.getPassword(localUser.getScreenName());
			credentials[2] = Boolean.FALSE.toString();
		}
		
		return credentials;
	}

}
