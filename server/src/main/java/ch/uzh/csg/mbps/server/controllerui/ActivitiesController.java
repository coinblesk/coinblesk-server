package ch.uzh.csg.mbps.server.controllerui;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.domain.Activities;
import ch.uzh.csg.mbps.server.service.ActivitiesService;

@Controller
@RequestMapping("/activities")
public class ActivitiesController {

	@RequestMapping(method = RequestMethod.GET)
	public String history() {
        return "html/activities";
    }
	
	@RequestMapping(value={"/logs"}, method = RequestMethod.GET)
	public @ResponseBody List<Activities> getlogs(){
		//TODO: mehmet page number should be passed too
		return ActivitiesService.getInstrance().getLogs(0);
	}
}
