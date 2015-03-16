package ch.uzh.csg.coinblesk.server.clientinterface;

import java.util.List;

import ch.uzh.csg.coinblesk.model.HistoryPayInTransactionUnverified;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

public interface IPayInTransactionUnverified {

	public void create(Transaction transaction) throws UserAccountNotFoundException;

	public List<HistoryPayInTransactionUnverified> getHistory(String username, int page) throws UserAccountNotFoundException;

	public long getHistoryCount(String username) throws UserAccountNotFoundException;

	public List<HistoryPayInTransactionUnverified> getLast5Transactions(String username) throws UserAccountNotFoundException;

}
