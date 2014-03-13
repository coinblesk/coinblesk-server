package ch.uzh.csg.mbps.server.auth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.util.StringUtils;

import ch.uzh.csg.mbps.server.util.Config;

/**
 * This handles an authentication attempt after it was successful. It is used to
 * set for example the session timeout.
 */
public class CustomAuthenticationSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
	private RequestCache requestCache = new HttpSessionRequestCache();
	
	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
		request.getSession().setMaxInactiveInterval(Config.SESSION_TIMEOUT);
		
		String targetUrlParam = getTargetUrlParameter();
		if (isAlwaysUseDefaultTargetUrl() || (targetUrlParam != null && StringUtils.hasText(request.getParameter(targetUrlParam)))) {
			requestCache.removeRequest(request, response);
		}
		
		clearAuthenticationAttributes(request);
	}
	
}
