package ch.uzh.csg.coinblesk.server.controllerui;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.coinblesk.server.util.Config;
import ch.uzh.csg.coinblesk.server.util.ServerProperties;
import ch.uzh.csg.coinblesk.server.web.response.WebRequestTransferObject;

@Controller
@RequestMapping("/login")
public class LoginController {
    
	@RequestMapping(value={"/url"}, method = RequestMethod.GET, produces="application/json")
	public @ResponseBody WebRequestTransferObject getURL(){
		WebRequestTransferObject response = new WebRequestTransferObject();
		response.setUrl(ServerProperties.getProperty("url.base"));
		response.setSuccessful(true);
		response.setMessage(Config.SUCCESS);
		return response;
	}
}
