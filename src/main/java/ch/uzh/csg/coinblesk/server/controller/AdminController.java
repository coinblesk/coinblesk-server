package ch.uzh.csg.coinblesk.server.controller;

import ch.uzh.csg.coinblesk.server.utils.ApiVersion;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.servlet.ServletContext;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;


@Controller
@RequestMapping(value = {"/admin", "/a"})
@ApiVersion({"v1", ""})
public class AdminController {
    
    private static org.slf4j.Logger LOG = LoggerFactory.getLogger(AdminController.class);
    
    @Autowired
    ServletContext context;
    
    @RequestMapping(method=RequestMethod.GET)
    public ModelAndView overview() {
    	Map<String, Object> model = new HashMap<>();
    	model.put("info", info());
    	ModelAndView mw = new ModelAndView("admin/overview", model);
    	return mw;
    }
    
    @RequestMapping(value = {"users"}, method=RequestMethod.GET)
    public ModelAndView users() {
    	ModelAndView mw = new ModelAndView("admin/users");
    	return mw;
    }
    
    @RequestMapping(value = {"tasks"}, method=RequestMethod.GET)
    public ModelAndView tasks() {
    	ModelAndView mw = new ModelAndView("admin/tasks");
    	return mw;
    }
    
    @RequestMapping(value = {"/info", "/i"}, method = RequestMethod.GET)
    @ResponseBody
    public String info() {
        LOG.debug("Info called");
        try {
			InputStream inputStream = context.getResourceAsStream("/META-INF/MANIFEST.MF");
			if (inputStream == null) {
				throw new IOException("Manifest resource not found.");
			}
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
