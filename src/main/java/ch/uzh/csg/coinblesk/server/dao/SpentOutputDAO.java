package ch.uzh.csg.coinblesk.server.dao;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaDelete;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.server.entity.SpentOutputs;

@Repository
public class SpentOutputDAO {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(SpentOutputDAO.class);
	
	final private static int ONE_DAY =  1000 * 60 * 60 * 24;
	
	@PersistenceContext()
    private EntityManager em;
	
	public void addOutput(final Transaction tx) {
		for (final TransactionInput txIn : tx.getInputs()) {
			final SpentOutputs outputs = new SpentOutputs();
			outputs.setTxOutPoint(txIn.getOutpoint().bitcoinSerialize());
			em.persist(outputs);
		}
		em.flush();
		LOGGER.debug("added transaciton {}",tx);
	}
	
	public boolean isDoubleSpend(final Transaction tx) {
		final CriteriaBuilder cb = em.getCriteriaBuilder();
		final CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		final CriteriaQuery<Long> qb = cq.select(cb.count(cq.from(SpentOutputs.class)));
		
		final Root<SpentOutputs> root = qb.from(SpentOutputs.class);

		final List<Predicate> predicates = new ArrayList<Predicate>();
        for (final TransactionInput txIn : tx.getInputs()) {
        	final Predicate condition = cb.equal(root.get("txOutPoint"), txIn.getOutpoint().bitcoinSerialize());
        	predicates.add(condition);
        }
        final Predicate finalCondition = cb.or(predicates.toArray(new Predicate[0]));
        qb.where(finalCondition);
        final long result = em.createQuery(cq).getSingleResult();
        LOGGER.debug("is transaction {} a doubel spend:{}",tx, result > 0);
        return result > 0;
	}
	
	public void removeOldEntries(final int nDays) {
		removeOldEntries(new Date(), nDays);
	}
	
	public void removeOldEntries(final Date date, final int nDays) {
		final CriteriaBuilder cb = em.getCriteriaBuilder();
		final CriteriaDelete<SpentOutputs> cq = cb.createCriteriaDelete(SpentOutputs.class);
		final Root<SpentOutputs> root = cq.from(SpentOutputs.class);
		final Date dateBefore = new Date(date.getTime() - (long) (ONE_DAY * nDays) ); //Subtract n days 
		final Predicate condition = cb.lessThan(root.get("timestamp"), dateBefore);
		cq.where(condition);
		final int result = em.createQuery(cq).executeUpdate();
		LOGGER.debug("old entries removed: {}", result);
	}
	
	final static public class DBTask {
		
		@Autowired
		private SpentOutputDAO spentOutputDAO;
		
		@Scheduled(fixedRate=ONE_DAY)
		@Transactional
		public void doTask() throws Exception {
			spentOutputDAO.removeOldEntries(1);		
		}
	}
	
}
