/*
 * Copyright 2016 The Coinblesk team and the CSG Group at University of Zurich
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.coinblesk.server.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.GsonHttpMessageConverter;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import com.coinblesk.server.utils.ApiVersionRequestMappingHandlerMapping;
import com.coinblesk.util.SerializeUtils;

@Configuration
@EnableWebMvc
public class WebMvcConfiguration extends WebMvcConfigurerAdapter {

    @Bean
    public RequestMappingHandlerMapping requestMappingHandlerMapping() {
        return new ApiVersionRequestMappingHandlerMapping();
    }

    @Override
    public void configureMessageConverters(List<HttpMessageConverter< ?>> converters) {
        GsonHttpMessageConverter gsonHttpMessageConverter = new GsonHttpMessageConverter();
        gsonHttpMessageConverter.setGson(SerializeUtils.GSON);
        converters.add(gsonHttpMessageConverter);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
    	registry.addViewController("/login").setViewName("login");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
    }

    @Override
    public void configureViewResolvers(ViewResolverRegistry registry) {
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();
        //no need for JSP
        //viewResolver.setViewClass(JstlView.class);
        //viewResolver.setPrefix("/WEB-INF/views/");
        //viewResolver.setSuffix(".jsp");
        viewResolver.setPrefix("/static/");
        viewResolver.setSuffix(".html");

        registry.viewResolver(viewResolver);
}

    /**
     * Configure ResourceHandlers to serve static resources like CSS/ Javascript etc...
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/static/**").addResourceLocations("/static/");

        // swagger configuration:
        registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
}
