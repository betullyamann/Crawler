/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.uci.ics.crawler4j.examples.multiple;

import com.sleepycat.je.tree.IN;
import edu.uci.ics.crawler4j.crawler.CrawlConfig;
import edu.uci.ics.crawler4j.crawler.CrawlController;
import edu.uci.ics.crawler4j.fetcher.PageFetcher;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtConfig;
import edu.uci.ics.crawler4j.robotstxt.RobotstxtServer;

/**
 * @author Yasser Ganjisaffar <lastname at gmail dot com>
 */

public class MultipleCrawlerController {

	public static void main(String[] args) throws Exception {

		//BFS --> chosen = -1
		//DFS --> chosen = 0
		//B-FS --> chosen 1
		Integer chosen = 1;
		Integer bestN= 5;

		/*
		if (args.length != 1) {
			System.out.println("Needed parameter: ");
			System.out.println("\t rootFolder (it will contain intermediate crawl data)");
			return;
		}
		*/



		/*
		 * crawlStorageFolder is a folder where intermediate crawl data is
		 * stored.
		 */
		//String crawlStorageFolder = args[0];

		CrawlConfig config1 = new CrawlConfig();
		//CrawlConfig config2 = new CrawlConfig();

		/*
		 * The two crawlers should have different storage folders for their
		 * intermediate data
		 */
		config1.setCrawlStorageFolder("C:\\crawler1");
		config1.setPolitenessDelay(1000);
		config1.setMaxPagesToFetch(1500);
		config1.setMaxDepthOfCrawling(5);

		/*
		 * We will use different PageFetchers for the two crawlers.
		 */
		PageFetcher pageFetcher1 = new PageFetcher(config1);
		//PageFetcher pageFetcher2 = new PageFetcher(config2);

		/*
		 * We will use the same RobotstxtServer for both of the crawlers.
		 */
		RobotstxtConfig robotstxtConfig = new RobotstxtConfig();
		RobotstxtServer robotstxtServer = new RobotstxtServer(robotstxtConfig, pageFetcher1);

		CrawlController controller1 = new CrawlController(config1, pageFetcher1, robotstxtServer, chosen, bestN);
		//CrawlController controller2 = new CrawlController(config2, pageFetcher2, robotstxtServer);

		String[] crawler1Domains = new String[] { "http://www.ics.uci.edu/", "http://www.cnn.com/" };
		String[] crawler2Domains = new String[] { "http://en.wikipedia.org/" };

		controller1.setCustomData(crawler1Domains);
		//controller2.setCustomData(crawler2Domains);

		controller1.addSeed("http://www.ics.uci.edu/");
		controller1.addSeed("http://www.cnn.com/");
		controller1.addSeed("http://www.ics.uci.edu/~yganjisa/");
		controller1.addSeed("http://www.cnn.com/POLITICS/");
//		controller1.addSeed("http://edition.cnn.com/US/OJ/");
//		controller1.addSeed("http://edition.cnn.com/EVENTS/1996/year.in.review/");
//		controller1.addSeed("http://www.mcspotlight.org/index.shtml");
//		controller1.addSeed("http://travelassist.com/mag/mag_home.html");
//		controller1.addSeed("http://www.excite.com/");
//		controller1.addSeed("http://www.instanet.com/");
//		controller1.addSeed("http://www.rdrop.com/");
//		controller1.addSeed("http://www.kli.org/");
//		controller1.addSeed("http://www.dolekemp96.org/");
//		controller1.addSeed("http://www.aliweb.com/");
//		controller1.addSeed("http://www.taco.com/");
//		controller1.addSeed("http://www.enterweb.org/");
//		controller1.addSeed("http://www.ifindit.com/");

		//controller2.addSeed("http://en.wikipedia.org/wiki/Main_Page");
		//controller2.addSeed("http://en.wikipedia.org/wiki/Obama");
		//controller2.addSeed("http://en.wikipedia.org/wiki/Bing");

		/*
		 * The first crawler will have 5 cuncurrent threads and the second
		 * crawler will have 7 threads.
		 */
		controller1.startNonBlocking(BasicCrawler.class, 1);
		//controller2.startNonBlocking(BasicCrawler.class, 7);

		controller1.waitUntilFinish();
		System.out.println("Crawler 1 is finished.");

		//controller2.waitUntilFinish();
		//System.out.println("Crawler 2 is finished.");
	}
}
