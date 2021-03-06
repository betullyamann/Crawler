package edu.uci.ics.crawler4j.frontier;

import com.sleepycat.je.*;
import edu.uci.ics.crawler4j.url.WebURL;
import edu.uci.ics.crawler4j.util.Util;

import javax.xml.bind.SchemaOutputResolver;
import java.sql.Array;
import java.util.*;

import static org.apache.commons.lang3.StringUtils.getLevenshteinDistance;
import static org.apache.commons.lang3.StringUtils.indexOf;

/**
 * @author Yasser Ganjisaffar <lastname at gmail dot com>
 */
public class WorkQueues {

    protected Database urlsDB = null;
    protected Environment env;

    protected boolean resumable;

    protected WebURLTupleBinding webURLBinding;

    protected final Object mutex = new Object();

    protected Integer chosen;
    protected WebURL url;
    protected int bestN;

    public WorkQueues(Environment env, String dbName, boolean resumable, Integer chosen, Integer bestN) throws DatabaseException {
        this.env = env;
        this.resumable = resumable;
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(resumable);
        dbConfig.setDeferredWrite(!resumable);
        urlsDB = env.openDatabase(null, dbName, dbConfig);
        webURLBinding = new WebURLTupleBinding();
        this.chosen = chosen;
        this.bestN = bestN;
    }

    public List<WebURL> get() throws DatabaseException {
        synchronized (mutex) {
            int matches = 0;
            List<WebURL> results = new ArrayList<WebURL>();

            Cursor cursor = null;
            OperationStatus result;
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            Transaction txn;
            if (resumable) {
                txn = env.beginTransaction(null, null);
            } else {
                txn = null;
            }

			/* BFS */
            if (chosen == -1) {
                try {
                    cursor = urlsDB.openCursor(txn, null);
                    //BFS algoritması, FIFO mantığıyla çalıştığından getFirst() metodu kullanılmıştır.
                    result = cursor.getFirst(key, value, null);

                        //Tek threadle çalışıldığından while kaldırılmıştır.
                        //sadece queuenun ilk verisi alınmaktadır.
                        if (value.getData().length > 0 && result == OperationStatus.SUCCESS) {
                            results.add(webURLBinding.entryToObject(value));
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
            }

            /* DFS */
            else if (chosen == 0) {
                try {
                    cursor = urlsDB.openCursor(txn, null);
                    result = cursor.getLast(key, value, null);

                    //DFS algoritması FILO mantığıyla çalışmaktadır.
                    //Elde edilen yeni değerler list sonuna eklendiği için, DFS algoritmasında elemanlar tek tek ele alınıyor.
                    if (result == OperationStatus.SUCCESS && value.getData().length > 0) {
                        results.add(webURLBinding.entryToObject(value));
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
            }

            /* B-FS */
            else if (chosen == 1) {
                try {
                    LinkedList<WebURL> urls = new LinkedList<>();
                    cursor = urlsDB.openCursor(txn, null);
                    result = cursor.getFirst(key, value, null);

                    //Databasedeki tüm URL'ler urls listine aktarılıyor.
                    while (result == OperationStatus.SUCCESS) {
                        if (value.getData().length > 0) {
                            urls.add(webURLBinding.entryToObject(value));
                        }
                        result = cursor.getNext(key, value, null);
                    }

                    //Urls listi relationSort metoduna gönderiliyor.
                    relationSort(urls);

                    //Sıralı dizinin ilk n -max- değeri results dizisine ekleniyor ve eklenen elemanlar urls dizisinden siliniyor.
                    int i = 0;
                    while (i < bestN && i < urls.size()) {
                        results.add(urls.removeFirst());
                        i++;
                    }

                    //Database, kullanılmayan urlleri koymak için temizleniyor
                    delete(urls.size());

                    //İşlenmeyecek elemanlar databasee geri yazılıyor.
                    for(i = 0; i < urls.size(); i++) {
                        put(urls.get(i));
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
            }
            return results;
        }
    }

    //URL ve parentURL'inin benzerliğine göre list elemanlarını sıralıyor.
    private void relationSort(LinkedList<WebURL> urls) {
        ArrayList<SortByRelation> relation = new ArrayList<>();
        int distance;
        //getLevenshteinDistance metodu, URL ve parentURL'inin benzerliğini Edit Distance yöntemiyle hesaplıyor.
        for (WebURL url : urls) {
            distance = getLevenshteinDistance(url.getURL(), getParentURL(url));
            relation.add(new SortByRelation(url, distance));
        }


        //Distance değerlerine göre relation dizisi sıralanıyor.
        Collections.sort(relation);

        //urls dizisine sıralanmış dizi yazılıyor.
        for (int i = 0; i < urls.size(); i++) {
            urls.set(i, relation.get(i).getUrl());
        }
    }


    // parentDocid'ye ait parentURL bulunuyor.
    private String getParentURL(WebURL webURL) {
        Cursor cursor = null;
        OperationStatus result;
        DatabaseEntry key = new DatabaseEntry(new byte[]{0, 0, 0, (byte) webURL.getParentDocid()}, 0, 4);
        DatabaseEntry value = new DatabaseEntry();
        Transaction txn;
        String parentURL = null;
        if (resumable) {
            txn = env.beginTransaction(null, null);
        } else {
            txn = null;
        }
        try {
            cursor = urlsDB.openCursor(txn, null);
            result = cursor.getFirst(key, value, null);

            if (result == OperationStatus.SUCCESS && value.getData().length > 0) {
                parentURL = webURLBinding.entryToObject(value).getURL();
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
        return parentURL;
    }

    public void delete(int count) throws DatabaseException {
        synchronized (mutex) {
            int matches = 0;

            Cursor cursor = null;
            OperationStatus result;
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry value = new DatabaseEntry();
            Transaction txn;
            if (resumable) {
                txn = env.beginTransaction(null, null);
            } else {
                txn = null;
            }

            /* BFS / B-FS */
            if (chosen == -1 || chosen == 1) {
                try {
                    cursor = urlsDB.openCursor(txn, null);
                    result = cursor.getFirst(key, value, null);

                    while (matches < count && result == OperationStatus.SUCCESS) {
                        cursor.delete();
                        matches++;
                        result = cursor.getNext(key, value, null);
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
            }

            /* DFS */
            else if (chosen == 0) {
                try {
                    cursor = urlsDB.openCursor(txn, null);
                    result = cursor.getLast(key, value, null);

                    //Elde edilen yeni değerler list sonuna eklendiği için DFS algoritmasında elemanlar tek tek ele alınıyor.
                    //Listin sonundaki değer siliniyor.
                    if (result == OperationStatus.SUCCESS && value.getData().length > 0) {
                        cursor.delete();
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
            }
        }
    }

    public void put(WebURL url) throws DatabaseException {
        byte[] keyData = Util.int2ByteArray(url.getDocid());
        //System.out.println("WorkQues.put = " + url.getDocid());
        DatabaseEntry value = new DatabaseEntry();
        webURLBinding.objectToEntry(url, value);
        Transaction txn;
        if (resumable) {
            txn = env.beginTransaction(null, null);
        } else {
            txn = null;
        }
        urlsDB.put(txn, new DatabaseEntry(keyData), value);
        if (resumable) {
            if (txn != null) {
                txn.commit();
            }
        }
    }

    public long getLength() {
        try {
            return urlsDB.count();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    public void sync() {
        if (resumable) {
            return;
        }
        if (urlsDB == null) {
            return;
        }
        try {
            urlsDB.sync();
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            urlsDB.close();
        } catch (DatabaseException e) {
            e.printStackTrace();
        }
    }
}
