/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.utils.ApiVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import javax.servlet.ServletContext;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

/**
 *
 * @author draft
 */

@RestController
@RequestMapping(value = {"/admin", "/a"})
@ApiVersion({"v1", ""})
public class AdminController {
    
    private static org.slf4j.Logger LOG = LoggerFactory.getLogger(AdminController.class);
    
    @Autowired
    ServletContext context;
    
    @RequestMapping(value = {"/info", "/i"}, method = RequestMethod.GET)
    @ResponseBody
    public String info() {
        LOG.debug("Info called");
        InputStream inputStream = context.getResourceAsStream("/META-INF/MANIFEST.MF");
        try {
            Properties prop = new Properties();
            prop.load( inputStream );
            List<String> keys = new ArrayList<>(prop.stringPropertyNames());
            Collections.sort(keys);
            StringBuilder sb = new StringBuilder();
            for(String key:keys) {
                sb.append(key).append(":[");
                sb.append(prop.get(key)).append("]  ");
            }
            return sb.toString().trim();
        } catch (IOException ex) {
           return "no manifest found";
        }        
    }
}
