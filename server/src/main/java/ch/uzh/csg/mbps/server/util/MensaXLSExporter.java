package ch.uzh.csg.mbps.server.util;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import ch.uzh.csg.mbps.server.dao.UserAccountDAO;
import ch.uzh.csg.mbps.server.domain.DbTransaction;
/**
 * Helper Class, only for Mensa Testrun! Will be deleted afterwards.
 *
 */
public class MensaXLSExporter {
	private static Logger LOGGER = Logger.getLogger(MensaXLSExporter.class);
	
	private static Session openSession() {
		SessionFactory sessionFactory = HibernateUtil.getSessionFactory();
		return sessionFactory.openSession();
	}
	
	@SuppressWarnings({ "deprecation", "unchecked" })
	protected static void doQuery(){
		try{
			File f = createFile("mensaExport");
			HSSFWorkbook hwb = new HSSFWorkbook();
			HSSFSheet sheet = hwb.createSheet("Report");
			HSSFRow rowhead = sheet.createRow((short) 0);
			rowhead.createCell((short) 0).setCellValue("seller_id");
			rowhead.createCell((short) 1).setCellValue("timestamp");
			rowhead.createCell((short) 2).setCellValue("input_currency");
			rowhead.createCell((short) 3).setCellValue("input_currency_amount");
			rowhead.createCell((short) 4).setCellValue("BTC_amount");
			Session session = openSession();
			session.beginTransaction();
			ch.uzh.csg.mbps.server.domain.UserAccount mensa = UserAccountDAO.getByUsername("MensaBinz");
			long mensaUserId = mensa.getId();
			ArrayList<DbTransaction> transactions = (ArrayList<DbTransaction>) session.createCriteria(DbTransaction.class).add(Restrictions.eq("sellerID", mensaUserId)).list();
			Date date = new Date();
			Calendar calendar = GregorianCalendar.getInstance();
			calendar.setTime(date);
			int day = calendar.get(Calendar.DAY_OF_MONTH);
			int month = calendar.get(Calendar.MONTH);
			session.close();
			Calendar transactionCalendar = GregorianCalendar.getInstance();
			int rowIndex = 1;
			for (DbTransaction tx : transactions) {
				Date transactionDate = tx.getTimestamp();
				transactionCalendar.setTime(transactionDate);
				int txDay = transactionCalendar.get(Calendar.DAY_OF_MONTH);
				int txMonth= transactionCalendar.get(Calendar.MONTH);;
				if (day == txDay && month == txMonth) {	
					HSSFRow row = sheet.createRow((short) rowIndex);
					row.createCell((short) 0).setCellValue(tx.getSellerId());
					row.createCell((short) 1).setCellValue(tx.getTimestamp().toString());
					row.createCell((short) 2).setCellValue(tx.getInputCurrency());
					BigDecimal inputCurrencyAmount = tx.getInputCurrencyAmount();
					if (inputCurrencyAmount == null) {
						inputCurrencyAmount = BigDecimal.ZERO;
					}
					row.createCell((short) 3).setCellValue(inputCurrencyAmount.doubleValue());
					row.createCell((short) 4).setCellValue(tx.getAmount().toString());
					rowIndex++;
				}
			}
			FileOutputStream fileOut = new FileOutputStream(f);
			hwb.write(fileOut);
			fileOut.close();
			Emailer.sendMensaReport(f);
			LOGGER.info(" Mensa Excel-Report file has been generated to " + f.getAbsolutePath());
		} catch ( Exception ex ) {
			LOGGER.error("MensaXLSExporter: " + ex.getMessage());
			Emailer.send("bitcoin@ifi.uzh.ch", "[MBPS] Error creating Mensa Export", ex.getMessage());
		}
	}
	private static File createFile(String filename) throws Exception {
		Properties properties = System.getProperties();
		String home = properties.get("user.home").toString();
		String separator = properties.get("file.separator").toString();
		String dirName = "mbps";
		String fileName = filename + "-" + System.currentTimeMillis() + ".xls";
		File dir = new File(home + separator + dirName);
		if (!dir.exists()) {
			if (!dir.mkdir())
				throw new Exception("Could not create directory: "+dir.getAbsolutePath());
		}
		File f = new File(dir, fileName);
		if (!f.createNewFile())
			throw new Exception("Could not create file: "+f.getAbsolutePath());
		return f;
	}
}