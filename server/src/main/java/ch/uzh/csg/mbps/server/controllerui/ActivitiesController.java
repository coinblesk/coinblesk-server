package ch.uzh.csg.mbps.server.controllerui;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ch.uzh.csg.mbps.server.clientinterface.IActivities;
import ch.uzh.csg.mbps.server.util.Config;
import ch.uzh.csg.mbps.server.web.response.ActivitiesTransferObject;

@Controller
@RequestMapping("/activities")
public class ActivitiesController {
	
	@Autowired
	private IActivities activitiesService;

	@RequestMapping(method = RequestMethod.GET)
	public String history() {
        return "html/activities";
    }
	
	@RequestMapping(value={"/logs"}, method = RequestMethod.POST, produces="application/json")
	@ResponseBody public ActivitiesTransferObject getlogs(){
		//TODO: mehmet page number should be passed too
		ActivitiesTransferObject response = new ActivitiesTransferObject();
		response.setActivitiessList(activitiesService.getLogs(0));
		response.setSuccessful(true);
		response.setMessage(Config.SUCCESS);
		return response;
	}
}