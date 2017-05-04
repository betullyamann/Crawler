package edu.uci.ics.crawler4j.frontier;

import org.apache.log4j.Logger;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;

import edu.uci.ics.crawler4j.url.WebURL;
import edu.uci.ics.crawler4j.util.Util;

/**
 * This class maintains the list of pages which are
 * assigned to crawlers but are not yet processed.
 * It is used for resuming a previous crawl. 
 * 
 * @author Yasser Ganjisaffar <lastname at gmail dot com>
 */
public class InProcessPagesDB extends WorkQueues {

	private static final Logger logger = Logger.getLogger(InProcessPagesDB.class.getName());

	public InProcessPagesDB(Environment env, Integer chosen, Integer max) throws DatabaseException {
		super(env, "InProcessPagesDB", true, chosen, max);
		long docCount = getLength();
		if (docCount > 0) {
			logger.info("Loaded " + docCount + " URLs that have been in process in the previous crawl.");
		}
	}

	public boolean removeURL(WebURL webUrl) {
		synchronized (mutex) {
			try {
				DatabaseEntry key = new DatabaseEntry(Util.int2ByteArray(webUrl.getDocid()));				
				Cursor cursor = null;
				OperationStatus result;
				DatabaseEntry value = new DatabaseEntry();
				Transaction txn = env.beginTransaction(null, null);
				try {
					cursor = urlsDB.openCursor(txn, null);
					result = cursor.getSearchKey(key, value, null);
					
					if (result == OperationStatus.SUCCESS) {
						result = cursor.delete();
						if (result == OperationStatus.SUCCESS) {
							return true;
						}
					}
				} catch (DatabaseException e) {
					if (txn != null) {
						txn.abort();
						txn = null;
					}
					throw e;
				} finally {
					if (cursor != null) {
						cursor.close();
					}
					if (txn != null) {
						txn.commit();
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}
