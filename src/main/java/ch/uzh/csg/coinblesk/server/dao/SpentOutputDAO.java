package ch.uzh.csg.coinblesk.server.dao;

import java.util.ArrayList;
import java.util.Calendar;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ch.uzh.csg.coinblesk.server.entity.SpentOutputs;
import ch.uzh.csg.coinblesk.server.service.ForexExchangeRateService;

@Repository
public class SpentOutputDAO {
	
	final private static int ONE_DAY =  1000 * 60 * 60 * 24;
	
	@PersistenceContext()
    private EntityManager em;
	
	@Transactional
	public void addOutput(Transaction tx) {
		for (TransactionInput txIn : tx.getInputs()) {
			SpentOutputs outputs = new SpentOutputs();
			outputs.setTxOutPoint(txIn.getOutpoint().bitcoinSerialize());
			em.persist(outputs);
		}
		em.flush();
	}
	
	@Transactional(readOnly=true)
	public boolean isDoubleSpend(Transaction tx) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaQuery<Long> cq = cb.createQuery(Long.class);
		CriteriaQuery<Long> qb = cq.select(cb.count(cq.from(SpentOutputs.class)));
		
        Root<SpentOutputs> root = qb.from(SpentOutputs.class);

        List<Predicate> predicates = new ArrayList<Predicate>();
        for (TransactionInput txIn : tx.getInputs()) {
        	Predicate condition = cb.equal(root.get("txOutPoint"), txIn.getOutpoint().bitcoinSerialize());
        	predicates.add(condition);
        }
        Predicate finalCondition = cb.or(predicates.toArray(new Predicate[0]));
        qb.where(finalCondition);
        long result = em.createQuery(cq).getSingleResult();
        return result > 0;
	}
	
	@Transactional
	public void removeOldEntries(int nDays) {
		removeOldEntries(new Date(), nDays);
	}
	
	@Transactional
	public void removeOldEntries(Date date, int nDays) {
		CriteriaBuilder cb = em.getCriteriaBuilder();
		CriteriaDelete<SpentOutputs> cq = cb.createCriteriaDelete(SpentOutputs.class);
		Root<SpentOutputs> root = cq.from(SpentOutputs.class);
		Date dateBefore = new Date(date.getTime() - (long) (ONE_DAY * nDays) ); //Subtract n days 
		Predicate condition = cb.lessThan(root.get("timestamp"), dateBefore);
		cq.where(condition);
		int result = em.createQuery(cq).executeUpdate();
		System.err.println("removed: "+result);
	}
	
	final static public class DBTask {
		
		@Autowired
		private SpentOutputDAO spentOutputDAO;
		
		@Scheduled(fixedRate=ONE_DAY)
		public void doTask() throws Exception {
			spentOutputDAO.removeOldEntries(1);		
		}
	}
	
}
