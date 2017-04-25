/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.coinblesk.server.auth;

import com.google.common.base.Strings;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import org.springframework.web.filter.GenericFilterBean;

/**
 *
 * @author Thomas Bocek
 */
public class DefaultVersionFilter extends GenericFilterBean {

    private final static String VERSION_MEDIA_PREFIX = "application/vnd.coinblesk.";

    private final String fullDefaultApiVersion;

    public DefaultVersionFilter(String defaultVersion) {
        this.fullDefaultApiVersion = VERSION_MEDIA_PREFIX.concat(defaultVersion);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) request;
        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(httpServletRequest) {
            @Override
            public String getHeader(String name) {
                if ("accept".equalsIgnoreCase(name)) {
                    final String value = request.getParameter(name);
                    if (Strings.isNullOrEmpty(value)) {
                        return fullDefaultApiVersion;
                    } else {
                        if (value.contains(VERSION_MEDIA_PREFIX)) {
                            return value;
                        } else {
                            return value.concat(", ").concat(fullDefaultApiVersion);
                        }
                    }
                }
                return super.getHeader(name);
            }
        };
        chain.doFilter(wrapper, response);
    }
}
