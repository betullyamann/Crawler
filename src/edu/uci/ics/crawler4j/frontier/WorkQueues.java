package edu.uci.ics.crawler4j.frontier;

import com.sleepycat.je.*;
import edu.uci.ics.crawler4j.url.WebURL;
import edu.uci.ics.crawler4j.util.Util;

import java.sql.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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

    public WorkQueues(Environment env, String dbName, boolean resumable, Integer chosen) throws DatabaseException {
        this.env = env;
        this.resumable = resumable;
        DatabaseConfig dbConfig = new DatabaseConfig();
        dbConfig.setAllowCreate(true);
        dbConfig.setTransactional(resumable);
        dbConfig.setDeferredWrite(!resumable);
        urlsDB = env.openDatabase(null, dbName, dbConfig);
        webURLBinding = new WebURLTupleBinding();
        this.chosen = chosen;
    }

    public List<WebURL> get(int max) throws DatabaseException {
        synchronized (mutex) {
            int matches = 0;
            List<WebURL> results = new ArrayList<WebURL>(max);

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
            if (chosen == 1) {
                try {
                    cursor = urlsDB.openCursor(txn, null);
                    result = cursor.getFirst(key, value, null);

                    while (matches < max && result == OperationStatus.SUCCESS) {
                        if (value.getData().length > 0) {
                            results.add(webURLBinding.entryToObject(value));
                            matches++;
                        }
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
            else if (chosen == 2) {
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
            else if (chosen == 3) {
                try {

                    ArrayList<WebURL> urls = new ArrayList<>();
                    cursor = urlsDB.openCursor(txn, null);
                    result = cursor.getCurrent(key, value, null);
                    //Databasedeki tüm URL'ler urls listine aktarılıp databaseten siliniyor.
                    while (result == OperationStatus.SUCCESS) {
                        if (value.getData().length > 0) {
                            urls.add(webURLBinding.entryToObject(value));
                        }
                        result = cursor.getNext(key, value, null);
                    }
                    delete(urls.size());

                    //Urls listi relationSort metoduna gönderiliyor.
                    relationSort(urls);

                    //Sıralı dizinin ilk n -max olarak atandı- değeri results dizisine ekleniyor.
                    for (int i = 0; i < max; i++) {
                        results.add(urls.get(i));
                    }

                    //İşlenmek üzere belirlenen sıralı n tane değer databasee geri yazılıyor.
                    for (int i = 0; i < max; i++) {
                        put(results.get(i));
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
    private void relationSort(ArrayList<WebURL> urls) {
        ArrayList<SortByRelation> relation = new ArrayList<>();
        int distance = Integer.MAX_VALUE;
        //calculateRelationWithEditDistance metodu, URL ve parentURL'inin benzerliğini Edit Distance yöntemiyle hesaplıyor.
        for (WebURL url : urls) {
            distance = calculateRelationWithEditDistance(url, getParentURL(url));
            relation.add(new SortByRelation(url, distance));
        }

        //calculateRelationWithEditDistance metodundan dönen distance değerlerine göre relation dizisi sıralanıyor.
        Collections.sort(relation);

        //urls dizisine sıralanmış dizi yazılıyor.
        for (int i = 0; i < urls.size(); i++) {
            urls.set(i, relation.get(i).getUrl());
        }
    }

    //URL ve parentURL'inin benzerliğini Edit Distance yöntemiyle hesaplıyor.
    private int calculateRelationWithEditDistance(WebURL url, String parentURL) {

        int len1 = url.getURL().length() + 1; // Matris satir sayısı
        int len2 = parentURL.length() + 1; // Matris sütun sayısı
        Integer[][] matrix = new Integer[len1][len2];

        for (int i = 0; i < len1; i++) {
            matrix[i][0] = 2 * i;
        }

        for (int i = 0; i < len1; i++) {
            matrix[0][i] = 2 * i;
        }

        for (int i = 1; i < len1; i++) {
            for (int j = 1; j < len2; j++) {
                matrix[i][j] = Math.min((Math.min(matrix[i - 1][j], matrix[i][j - 1]) + 2), (matrix[i - 1][j - 1] + (url.getURL().charAt(i - 1) != parentURL.charAt(j - 1) ? 1 : 0)));
            }
        }
        return matrix[len1 - 1][len2 - 1];
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

            if (result == OperationStatus.SUCCESS && value.getSize() > 0) {
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

            /* BFS */
            if (chosen == 1 || chosen == 3) {
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
            else if (chosen == 2) {
                try {
                    cursor = urlsDB.openCursor(txn, null);
                    result = cursor.getLast(key, value, null);

                    //Elde edilen yeni değerler list sonuna eklendiği için DFS algoritamsında elemanlar tek tek ele alınıyor.
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
