package ch.uzh.csg.mbps.server.cors;

import java.io.IOException;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

import com.google.common.collect.Sets;

public class CorsFilter extends OncePerRequestFilter {


	private Set<String> whitelist = Sets.newHashSet("[AllowedOrigin1]","[AllowedOrigin2]");
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {

//		response.setHeader("Access-Control-Allow-Origin", "*");
//	    response.setHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE");
//	    response.setHeader("Access-Control-Max-Age", "3600");
//	    response.setHeader("Access-Control-Allow-Headers", "x-requested-with");
		
		if (request.getMethod().equals("OPTIONS")) {
			response.addHeader("Access-Control-Allow-Methods","GET, POST, PUT, DELETE");
			response.addHeader("Access-Control-Allow-Headers","origin, content-type, accept");
			response.addHeader("Access-Control-Max-Age", "600");
		}

		String origin = request.getHeader("Origin");

		if (origin != null && whitelist.contains(origin)) {
			response.addHeader("Access-Control-Allow-Origin", origin);
			response.addHeader("Access-Control-Allow-Credentials", "true");
		}

		filterChain.doFilter(request, response);
	}
	
	@Override
    public void destroy() {}
}