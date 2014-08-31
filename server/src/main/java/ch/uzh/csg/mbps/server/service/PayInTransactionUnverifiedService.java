package ch.uzh.csg.mbps.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.mbps.model.HistoryPayInTransaction;
import ch.uzh.csg.mbps.server.dao.PayInTransactionUnverifiedDAO;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.PayInTransactionUnverified;
import ch.uzh.csg.mbps.server.domain.UserAccount;
import ch.uzh.csg.mbps.server.util.exceptions.UserAccountNotFoundException;

import com.azazar.bitcoin.jsonrpcclient.Bitcoin.Transaction;

@Service
public class PayInTransactionUnverifiedService {

	@Autowired
	private PayInTransactionUnverifiedDAO payInTransactionUnverifiedDAODAO;
	@Autowired
	private UserAccountDAO userAccountDAO;
	
	@Transactional
	public void create(Transaction transaction) throws UserAccountNotFoundException {
		long userID = userAccountDAO.getByBTCAddress(transaction.address()).getId();
		PayInTransactionUnverified pit = new PayInTransactionUnverified(userID, transaction);
		if (payInTransactionUnverifiedDAODAO.isNew(pit)) {
			payInTransactionUnverifiedDAODAO.createPayInTransaction(pit);
		}
    }
	
	@Transactional(readOnly = true)
	public List<HistoryPayInTransaction> getHistory(String username,
			int page) throws UserAccountNotFoundException {
		return payInTransactionUnverifiedDAODAO.getHistory(username, page);
	}

	@Transactional(readOnly = true)
	public long getHistoryCount(String username)
			throws UserAccountNotFoundException {
		UserAccount userAccount = userAccountDAO.getByUsername(username);
		return payInTransactionUnverifiedDAODAO.getHistoryCount(userAccount);
	}

	@Transactional(readOnly = true)
	public List<HistoryPayInTransaction> getLast5Transactions(String username) throws UserAccountNotFoundException {
		UserAccount userAccount = userAccountDAO.getByUsername(username);
		return payInTransactionUnverifiedDAODAO.getLast5Transactions(userAccount);
	}

}
