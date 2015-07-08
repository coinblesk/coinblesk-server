package ch.uzh.csg.coinblesk.server.dao;

import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.apache.log4j.Logger;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.server.domain.SignedInput;

/**
 * DatabaseAccessObject for storing bitcoin transaction inputs of time-locked
 * transactions that have been signed by the server.
 * 
 */
@Repository
public class SignedInputDAO {
    private static Logger LOGGER = Logger.getLogger(SignedInputDAO.class);

    @PersistenceContext()
    private EntityManager em;

    /**
     * Get the lock time (block height) of a previously signed refund
     * transaction input. A transaction input is identified by the hash of the
     * referencing transaction input, and the index of the output in the
     * transaction. For more information, see
     * https://bitcoin.org/en/glossary/outpoint
     * 
     * @param txHash
     *            the hash of the referencing transaction
     * @param outputIndex
     *            the index of the output
     * @return Long.MAX_VALUE if the input wasn't found, or the lock time if
     *         this input has been signed earlier.
     */
    public long getLockTime(byte[] txHash, long outputIndex) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<SignedInput> qb = cb.createQuery(SignedInput.class);
        Root<SignedInput> root = qb.from(SignedInput.class);

        Predicate condition1 = cb.equal(root.get("txHash"), txHash);
        Predicate condition2 = cb.equal(root.get("outputIndex"), outputIndex);
        Predicate finalCondition = cb.and(condition1, condition2);


        qb.where(finalCondition);

        SignedInput signedInput = getSingle(qb, em);

        return signedInput == null ? Long.MAX_VALUE : signedInput.getLockTime();
    }
    
    /**
     * Persists a time-locked input that has been signed by the server.
     * @param txHash
     * @param outputIndex
     * @param lockTime
     */
    @Transactional
    public void addSignedInput(byte[] txHash, long outputIndex, long lockTime) {
        
        // check if signed input already exists
        SignedInput savedInput = getSignedInput(txHash, outputIndex);
        
        if(savedInput != null) {
            if(savedInput.getLockTime() <= lockTime) {
                // input was already signed and saved with a lower lockTime -> ignore
                
            } else {
                // update the lock time
                savedInput.setLockTime(lockTime);
                em.refresh(savedInput);
            }
        } else {
            // never seen this output before -> save it
            SignedInput signedInput = new SignedInput(lockTime, txHash, outputIndex);
            em.persist(signedInput);
            
        }
        
        em.flush();
        
    }
    
    @Transactional
    public void removeSignedInput(byte[] txHash, int outputIndex) {
        SignedInput savedInput = getSignedInput(txHash, outputIndex);
        em.remove(savedInput);
        em.flush();
    }
    
    private SignedInput getSignedInput(byte[] txHash, long outputIndex) {
        CriteriaBuilder cb = em.getCriteriaBuilder();
        CriteriaQuery<SignedInput> qb = cb.createQuery(SignedInput.class);
        Root<SignedInput> root = qb.from(SignedInput.class);

        Predicate condition1 = cb.equal(root.get("txHash"), txHash);
        Predicate condition2 = cb.equal(root.get("outputIndex"), outputIndex);
        Predicate finalCondition = cb.and(condition1, condition2);


        qb.where(finalCondition);

        return getSingle(qb, em);
    }

    private <K> K getSingle(CriteriaQuery<K> cq, EntityManager em) {
        List<K> list = em.createQuery(cq).getResultList();
        if (list.size() == 0) {
            return null;
        }
        return list.get(0);
    }

}
