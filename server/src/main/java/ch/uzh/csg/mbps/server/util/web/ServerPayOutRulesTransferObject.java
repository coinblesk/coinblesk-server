package ch.uzh.csg.mbps.server.util.web;

import java.util.ArrayList;

import ch.uzh.csg.mbps.server.domain.ServerPayOutRule;

public class ServerPayOutRulesTransferObject {
	private ArrayList<ServerPayOutRule> serverPayOutRulesList;
	private String message;
	
	public ServerPayOutRulesTransferObject(){
		serverPayOutRulesList = new ArrayList<ServerPayOutRule>();
		message = "";
	}
	
	public ArrayList<ServerPayOutRule> getPayOutRulesList() {
		return serverPayOutRulesList;
	}

	public void setPayOutRulesList(ArrayList<ServerPayOutRule> list) {
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