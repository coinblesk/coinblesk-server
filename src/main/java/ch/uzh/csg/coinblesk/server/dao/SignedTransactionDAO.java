package ch.uzh.csg.coinblesk.server.dao;

import java.util.ArrayList;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;

import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import ch.uzh.csg.coinblesk.server.entity.SignedTransactions;
import ch.uzh.csg.coinblesk.server.entity.SpentOutputs;

@Repository
public class SignedTransactionDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(SignedTransactionDAO.class);

    @PersistenceContext()
    private EntityManager em;

    @Transactional
    public void addSignedTransaction(final Transaction tx) {

        final SignedTransactions signedTx = new SignedTransactions();
        signedTx.setTxHash(tx.getHash().getBytes());
        em.persist(signedTx);
        em.flush();

        LOGGER.debug("added signed transaciton {}", tx);
    }

    /**
     * Checks if the inputs of this transactions were signed by the server
     * 
     * @param tx
     * @return true if all inputs of the transactions were signed by the server
     */
    public boolean allInputsServerSigned(Transaction tx) {
        final CriteriaBuilder cb = em.getCriteriaBuilder();
        final CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        final CriteriaQuery<Long> qb = cq.select(cb.count(cq.from(SignedTransactions.class)));

        final Root<SignedTransactions> root = qb.from(SignedTransactions.class);

        final List<Predicate> predicates = new ArrayList<Predicate>();
        for (final TransactionInput txIn : tx.getInputs()) {
            final Predicate condition = cb.equal(root.get("txHash"), txIn.getOutpoint().getHash().getBytes());
            predicates.add(condition);
        }
        final Predicate finalCondition = cb.or(predicates.toArray(new Predicate[0]));
        qb.where(finalCondition);
        final long result = em.createQuery(cq).getSingleResult();
        return result > 0;
    }

}
