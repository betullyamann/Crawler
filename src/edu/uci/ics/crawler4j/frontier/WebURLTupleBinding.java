package edu.uci.ics.crawler4j.frontier;

import com.sleepycat.bind.tuple.TupleBinding;
import com.sleepycat.bind.tuple.TupleInput;
import com.sleepycat.bind.tuple.TupleOutput;

import edu.uci.ics.crawler4j.url.WebURL;

public class WebURLTupleBinding extends TupleBinding<WebURL> {

	@Override
	public WebURL entryToObject(TupleInput input) {
		WebURL webURL = new WebURL();
		webURL.setURL(input.readString());
		webURL.setDocid(input.readInt());
		webURL.setParentDocid(input.readInt());
		webURL.setDepth(input.readShort());
		return webURL;
	}

	@Override
	public void objectToEntry(WebURL url, TupleOutput output) {		
		output.writeString(url.getURL());
		output.writeInt(url.getDocid());
		output.writeInt(url.getParentDocid());
		output.writeShort(url.getDepth());
	}
}
