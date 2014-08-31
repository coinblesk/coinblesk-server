package ch.uzh.csg.mbps.server.util;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import ch.uzh.csg.mbps.model.HistoryTransaction;
import ch.uzh.csg.mbps.server.clientinterface.ITransaction;
/**
 * Helper Class, only for Mensa Testrun! Will be deleted afterwards.
 *
 */
@Controller
public class MensaXLSExporter {
	private static Logger LOGGER = Logger.getLogger(MensaXLSExporter.class);

	@Autowired
	private ITransaction transactionService;
	
	public void doQuery(){
		try{
			File f = createFile("mensaExport");
			HSSFWorkbook hwb = new HSSFWorkbook();
			HSSFSheet sheet = hwb.createSheet("Report");
			HSSFRow rowhead = sheet.createRow((short) 0);
			rowhead.createCell((short) 0).setCellValue("buyer / seller");
			rowhead.createCell((short) 1).setCellValue("timestamp");
			rowhead.createCell((short) 2).setCellValue("input_currency");
			rowhead.createCell((short) 3).setCellValue("input_currency_amount");
			rowhead.createCell((short) 4).setCellValue("BTC_amount");
			
			List<HistoryTransaction> transactions = transactionService.getAll("MensaBinz");
			
			Date date = new Date();
			Calendar calendar = GregorianCalendar.getInstance();
			calendar.setTime(date);
			int day = calendar.get(Calendar.DAY_OF_MONTH);
			int month = calendar.get(Calendar.MONTH);
	
			
			
			Calendar transactionCalendar = GregorianCalendar.getInstance();
			int rowIndex = 1;
			for (HistoryTransaction tx : transactions) {
				Date transactionDate = tx.getTimestamp();
				transactionCalendar.setTime(transactionDate);
				int txDay = transactionCalendar.get(Calendar.DAY_OF_MONTH);
				int txMonth= transactionCalendar.get(Calendar.MONTH);;
				if (day == txDay && month == txMonth) {	
					HSSFRow row = sheet.createRow((short) rowIndex);
					row.createCell((short) 0).setCellValue(tx.getBuyer() + "/" + tx.getSeller());
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