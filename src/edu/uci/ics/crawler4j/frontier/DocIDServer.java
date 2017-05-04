package edu.uci.ics.crawler4j.frontier;

import org.apache.log4j.Logger;

import com.sleepycat.je.*;

import edu.uci.ics.crawler4j.crawler.Configurable;
import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.util.Util;

public class DocIDServer extends Configurable {

	protected static final Logger logger = Logger.getLogger(DocIDServer.class.getName());
	
	protected Database docIDsDB = null;
	
	protected final Object mutex = new Object();

	protected int lastDocID;

	public DocIDServer(Environment env, CrawlConfig config) throws DatabaseException {
		super(config);
		DatabaseConfig dbConfig = new DatabaseConfig();
		dbConfig.setAllowCreate(true);
		dbConfig.setTransactional(config.isResumableCrawling());
		dbConfig.setDeferredWrite(!config.isResumableCrawling());
		docIDsDB = env.openDatabase(null, "DocIDs", dbConfig);
		if (config.isResumableCrawling()) {
			int docCount = getDocCount();
			if (docCount > 0) {
				logger.info("Loaded " + docCount + " URLs that had been detected in previous crawl.");
				lastDocID = docCount;
			}
		} else {
			lastDocID = 0;
		}
	}

	/**
	 * Returns the docid of an already seen url.
     *
     * @param url the URL for which the docid is returned.
     * @return the docid of the url if it is seen before. Otherwise -1 is returned.
     */
	public int getDocId(String url) {
		synchronized (mutex) {
			if (docIDsDB == null) {
				return -1;
			}
			OperationStatus result;
			DatabaseEntry value = new DatabaseEntry();
			try {
				DatabaseEntry key = new DatabaseEntry(url.getBytes());
				result = docIDsDB.get(null, key, value, null);

				if (result == OperationStatus.SUCCESS && value.getData().length > 0) {
					return Util.byteArray2Int(value.getData());
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			return -1;
		}
	}



	public int getNewDocID(String url) {
		synchronized (mutex) {
			try {
				// Make sure that we have not already assigned a docid for this URL
				int docid = getDocId(url);
				if (docid > 0) {
					return docid;
				}

				lastDocID++;
				docIDsDB.put(null, new DatabaseEntry(url.getBytes()), new DatabaseEntry(Util.int2ByteArray(lastDocID)));
				return lastDocID;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return -1;
		}
	}
	
	public boolean isSeenBefore(String url) {
		return getDocId(url) != -1;
	}

	public int getDocCount() {
		try {
			return (int) docIDsDB.count();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
		return -1;
	}

	public void sync() {
		if (config.isResumableCrawling()) {
			return;
		}
		if (docIDsDB == null) {
			return;
		}
		try {
			docIDsDB.sync();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}

	public void close() {
		try {
			docIDsDB.close();
		} catch (DatabaseException e) {
			e.printStackTrace();
		}
	}
}
