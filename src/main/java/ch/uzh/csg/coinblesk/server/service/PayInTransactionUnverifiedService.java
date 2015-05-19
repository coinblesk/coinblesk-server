package ch.uzh.csg.coinblesk.server.service;

import java.util.List;

import org.bitcoinj.core.Transaction;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.model.HistoryPayInTransactionUnverified;
import ch.uzh.csg.coinblesk.server.clientinterface.IPayInTransactionUnverified;
import ch.uzh.csg.coinblesk.server.dao.PayInTransactionUnverifiedDAO;
import ch.uzh.csg.coinblesk.server.dao.UserAccountDAO;
import ch.uzh.csg.coinblesk.server.domain.UserAccount;
import ch.uzh.csg.coinblesk.server.util.exceptions.UserAccountNotFoundException;

@Service
public class PayInTransactionUnverifiedService implements IPayInTransactionUnverified {

    @Autowired
    private PayInTransactionUnverifiedDAO payInTransactionUnverifiedDAODAO;
    @Autowired
    private UserAccountDAO userAccountDAO;

    @Override
    @Transactional
    public void create(Transaction transaction) throws UserAccountNotFoundException {

        // TODO: rewrite after change to bitcoinj
        assert (false);

//        long userID = userAccountDAO.getByBTCAddress(transaction.address()).getId();
//        PayInTransactionUnverified pit = new PayInTransactionUnverified(userID, transaction);
//        if (payInTransactionUnverifiedDAODAO.isNew(pit)) {
//            payInTransactionUnverifiedDAODAO.createPayInTransaction(pit);
//        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryPayInTransactionUnverified> getHistory(String username, int page) throws UserAccountNotFoundException {
        return payInTransactionUnverifiedDAODAO.getHistory(username, page);
    }

    @Override
    @Transactional(readOnly = true)
    public long getHistoryCount(String username) throws UserAccountNotFoundException {
        UserAccount userAccount = userAccountDAO.getByUsername(username);
        return payInTransactionUnverifiedDAODAO.getHistoryCount(userAccount);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HistoryPayInTransactionUnverified> getLast5Transactions(String username) throws UserAccountNotFoundException {
        UserAccount userAccount = userAccountDAO.getByUsername(username);
        return payInTransactionUnverifiedDAODAO.getLast5Transactions(userAccount);
    }
}