import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Queue;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.PriorityQueue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.StringArrayOptionHandler;

/**
 * <p>A subject-specific web crawler.</p>
 * 
 * <p>Crawls the web from a starting URL with priority based on the query terms, then 
 * downloads the crawled pages.</p>
 * 
 * <p>Usage:<br>
 * Provide arguments for the entry point 
 * "-url STARTING_URL -query QUERY_TERMS -docs DOWNLOAD_PATH [-max MAX_PAGES] [-trace]" 
 * (options can be abbreviated)</p>
 * 
 * @author Chenyang Tang
 *
 */
public class Crawler {
  
  /*
   * command-line arguments for the entry point
   */
  @Option(name = "-url", aliases = "-u", required = true, 
          usage = "The starting URL for crawling. Required option.")
  private String startingUrl;
  
  @Option(name = "-docs", aliases = "-d", required = true, 
      usage = "Path for saving downloaded pages. Required option.")
  private Path downloadPath;

  @Option(name = "-query", aliases = "-q", required = false, 
          handler = StringArrayOptionHandler.class, usage = "List of query terms.")
  private String[] queryTerms;
  
  @Option(name = "-max", aliases = "-m", required = false, 
      usage = "Maximum number of pages to crawl (default: 50).")
  private int maxPage = 50;

  @Option(name = "-trace", aliases = "-t", required = false, 
      usage = "Use this flag to print out trace details.")
  private boolean trace = false;
  
  @Option(name = "-help", aliases = "-h", required = false, 
          usage = "Print this help text.")
  private boolean printHelp = false;

  /**
   * Class to store a URL and its score. 
   * 
   * @author Chenyang Tang
   *
   */
  private static class ScoredUrl {
    
    public String url;
    public int score;
    
    /**
     * Constructor
     * 
     * @param url URL string
     * @param score score
     */
    public ScoredUrl(String url, int score) {
      this.url = url;
      this.score = score;
    }
    
    /*
     * Only compares the URL.
     */
    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      ScoredUrl other = (ScoredUrl) obj;
      if (url == null) {
        if (other.url != null)
          return false;
      } else if (!url.equals(other.url))
        return false;
      return true;
    }
  }
  
  /**
   * Calculate the score of a URL according to given query terms
   * 
   * @param url URL
   * @param queryTerms list of query terms to score the URL
   * @param page the page where the URL resides
   * @return the score
   */
  public static int scoreUrl(String url, String[] queryTerms, Document page) {
    
    
    return 0;
  }
  
  private void startCrawl() {
    Queue<ScoredUrl> urlQueue = new PriorityQueue<ScoredUrl>(10, (a, b) -> b.score - a.score);
    Set<String> visited = new HashSet<String>();
    urlQueue.offer(new ScoredUrl(startingUrl, 0));
    while (!urlQueue.isEmpty() && visited.size() < maxPage) {
      // Retrieve a page from the priority queue
      ScoredUrl scoredUrl = urlQueue.poll();
      if (trace) System.out.println("Downloading:" + scoredUrl.url + ". Score = " + scoredUrl.score);
      Document page;
      try {
        page = downloadPage(scoredUrl.url, downloadPath);
      } catch (IOException e) {
        continue;
      }
      if (trace) System.out.println("Received: " + scoredUrl.url);
      visited.add(scoredUrl.url);
      if (visited.size() >= maxPage) break;
      
      // get all links from the retrieved page
      Elements links = page.select("a[href]");
      links.forEach( link -> {
        String linkUrl = link.attr("abs:href"); // Note the "abs:" attribute prefix used to automatically 
                                                // resolve an absolute URL if the link is a relative URL
        if (visited.contains(linkUrl)) return;
        int newScore = scoreUrl(linkUrl, queryTerms, page);
        if (urlQueue.contains(new ScoredUrl(linkUrl, 0))) {
          urlQueue.forEach( existing -> {
            if (linkUrl.equals(existing.url)) {
              existing.score += newScore;
              if (trace) System.out.println("Adding " + newScore + " to score of " + linkUrl);
            }
          } );
        } else {
          urlQueue.offer(new ScoredUrl(linkUrl, newScore));
          if (trace) System.out.println("Adding to queue: " + linkUrl + " with score " + newScore);
        }
      } );
    }
  }
  
  /**
   * Download a page.
   * @param url URL of the page.
   * @param downloadPath download save path
   * @return document object of the downloaded page
   * @throws IOException 
   */
  private Document downloadPage(String url, Path downloadPath) throws IOException {
    Document page = Jsoup.connect(url).maxBodySize(Integer.MAX_VALUE).get();
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(downloadPath + "/" + url))) {
      writer.write(page.data());
    }
    return page;
  }
  
  private int parseArgs(String[] args) {
    final CmdLineParser args4jCmdLineParser = new CmdLineParser(this);
    try {
      args4jCmdLineParser.parseArgument(args);
    } catch (final CmdLineException e) {
      System.err.println(e.getMessage());
      System.err.println("Usage:");
      args4jCmdLineParser.printUsage(System.err);
      return 2;
    }
    
    if (printHelp) {
      System.err.println("Usage:");
      args4jCmdLineParser.printUsage(System.err);
      return 1;
    }
    
    return 0;
  }
  
  /**
   * Entry point.
   * 
   * @param args command-line arguments
   */
  public static void main(String[] args) {
    final Crawler crawler = new Crawler();
    int status;
    status = crawler.parseArgs(args);
    if (status != 0) System.exit(status);
  }

}
