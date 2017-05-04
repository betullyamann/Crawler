package edu.uci.ics.crawler4j.frontier;

import edu.uci.ics.crawler4j.url.WebURL;

/**
 * Created by betulyaman on 2.05.2017.
 */
public class SortByRelation implements Comparable<SortByRelation>{

    private WebURL url;
    private int distance;

    public SortByRelation(WebURL url, int distance){
        this.url = url;
        this.distance = distance;
    }

    public WebURL getUrl() {
        return url;
    }

    public void setUrl(WebURL url) {
        this.url = url;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    @Override
    public int compareTo(SortByRelation o) {
        return new Integer(this.getDistance()).compareTo(o.getDistance());
    }
}
