package ch.uzh.csg.mbps.server.util.web;

import java.util.List;

import ch.uzh.csg.mbps.server.domain.ServerPayOutRule;

public class ServerPayOutRulesTransferObject {
	public enum Status {
		REQUEST, REPLY_SUCCESS, REPLY_FAILED
	}
	
	private Status status = Status.REQUEST;
	private List<ServerPayOutRule> serverPayOutRulesList;
	private String message;

	public ServerPayOutRulesTransferObject(){
	}
	
	public boolean isSuccessful() {
		return status == Status.REPLY_SUCCESS;
	}

	public void setSuccessful(boolean successful) {
		this.status = successful ? Status.REPLY_SUCCESS : Status.REPLY_FAILED;
	}
	
	public List<ServerPayOutRule> getPayOutRulesList() {
		return serverPayOutRulesList;
	}

	public void setPayOutRulesList(List<ServerPayOutRule> list) {
		this.serverPayOutRulesList = list;
	}
	
	public void setServerPayOutRule(ServerPayOutRule rule){
		this.serverPayOutRulesList.add(rule);
	}
	
	public String getMessage(){
		return message;
	}
	
	public void setMessage(String message){
		this.message = message;
	}
}