import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.HashSet;
import java.util.PriorityQueue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
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
 * <p>Downloaded files will be saved in DOWNLOAD_PATH in their original directory structure; 
 * pages with URLs that represent a directory will be written as "index.html" 
 * in their corresponding directories.</p>
 * 
 * <p>Illegal characters in filenames will be changed or omitted depending on the character. 
 * For example, < and > are changed to [ and ] respectively. 
 * *?"| are all changed to underscore, while colons are omitted. </p>
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
  
  
  private final boolean DEBUG = false; // Debug flag

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
      } else if (! url.equals(other.url))
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
    
    // Add starting URL
    urlQueue.offer(new ScoredUrl(startingUrl, 0));
    
    // Crawler loop
    while (! urlQueue.isEmpty() && visited.size() < maxPage) {
      // Retrieve a page from the priority queue
      ScoredUrl scoredUrl = urlQueue.poll();
      
      // Skip URL if it is not allowed to visit by robot
      if (! robotSafe(scoredUrl.url)) continue;
      
      // Download the page
      if (trace) System.out.println("Downloading: " + scoredUrl.url + 
                                    ". Score = " + scoredUrl.score);
      Document page = downloadPage(scoredUrl.url, downloadPath);
      if (page == null) continue; // This can happen if the link is invalid (eg. 404), 
                                  // or other network error
      if (trace) System.out.println("Received: " + scoredUrl.url);
      visited.add(scoredUrl.url);
      if (visited.size() >= maxPage) break;
      
      // get all links from the retrieved page
      Elements links = page.select("a[href]");
      links.forEach( link -> {
        String linkUrl = link.attr("abs:href"); // The "abs:" attribute prefix is used to automatically 
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
   * @return document object of the downloaded page; if downloading fails (eg. 404) return null
   */
  private Document downloadPage(String url, Path downloadPath) {
    // Download page content
    Document page;
    try {
      page = Jsoup.connect(url).maxBodySize(Integer.MAX_VALUE).get();
    } catch (IOException e) {
      if (trace) System.out.println(
          "Network error (eg. 404) when retrieving the page: " + url + ". Skipped this page.");
      return null;
    }
    
    // Construct a file path to save the downloaded page
    url = sanitizeFilename(url);
    String filePath = downloadPath.toAbsolutePath() + "/" + url;
    Path finalPath = Paths.get(filePath).normalize();
    // If the URL is a directory, restore the "index.html" filename.
    // Otherwise the file will be stored without an extension;
    // this will cause collision between filename and directory name,
    // which are illegal to be the same in both Windows and Unix-like systems.
    if (! finalPath.getFileName().toString().contains(".")) {
      finalPath = Paths.get(filePath + "index.html");
    }
    
    // Test if the parent directories of the constructed path exists; if not, create them
    File directory = new File(finalPath.getParent().toString());
    if (! directory.isDirectory()){
      directory.mkdirs();
    }

    // Write to file
    try (BufferedWriter writer = new BufferedWriter(new FileWriter(finalPath.toString()))) {
      writer.write(page.outerHtml());
    } catch (IOException e) {
      System.out.println("Write to disk failed: " + finalPath.toString());
    }
    return page;
  }
  
  /**
   * Change or strip characters not allowed in filenames
   * 
   * @param filePath input filename
   * @return output filename
   */
  private static String sanitizeFilename(String filePath) {
    filePath = filePath.replaceAll("\\:", "");
    filePath = filePath.replaceAll("[\\*\\?\\\"\\|]", "_");
    filePath = filePath.replaceAll("<", "[");
    filePath = filePath.replaceAll(">", "]");
    filePath = filePath.replaceAll("\\\\", "/");
    filePath = filePath.replaceAll("/+", "/");
    return filePath;
  }
  
  /**
   * Test if a URL allows robot according to the site's "robots.txt" file.
   * 
   * This method is a slightly modified version of Mr. Davis's code at 
   * https://cs.nyu.edu/courses/spring16/CSCI-GA.2580-001/WebCrawler.java.txt
   * 
   * @param url URL to test
   * @return true if allowed, false if not allowed.
   */
  private boolean robotSafe(String urlStr) {
    URL url;
    try {
      url = new URL(urlStr);
    } catch (MalformedURLException e1) {
      // Malformed URL. Don't trust it
      return false;
    }
    
    // form URL of the robots.txt file
    String strHost = url.getHost();
    String strRobot = "http://" + strHost + "/robots.txt";
    URL urlRobot;
    try { 
      urlRobot = new URL(strRobot);
    } catch (MalformedURLException e) {
      // something weird is happening, so don't trust it
      return false;
    }

    if (DEBUG) System.out.println("Checking robot protocol " + 
                                   urlRobot.toString());
    String strCommands;
    try (InputStream urlRobotStream = urlRobot.openStream()) {
      // read in entire file
      byte b[] = new byte[1000];
      int numRead = urlRobotStream.read(b);
      strCommands = new String(b, 0, numRead);
      while (numRead != -1) {
        numRead = urlRobotStream.read(b);
        if (numRead != -1) {
          String newCommands = new String(b, 0, numRead);
          strCommands += newCommands;
        }
      }
    } catch (IOException e) {
        // if there is no robots.txt file, it is OK to search
        return true;
    }
    if (DEBUG) System.out.println(strCommands);

    // assume that this robots.txt refers to us and 
    // search for "Disallow:" commands.
    String strURL = url.getFile();
    int index = 0;
    final String DISALLOW = "Disallow:";
    while ((index = strCommands.indexOf(DISALLOW, index)) != -1) {
      index += DISALLOW.length();
      String strPath = strCommands.substring(index);
      StringTokenizer st = new StringTokenizer(strPath);

      if (! st.hasMoreTokens())
        break;
      
      String strBadPath = st.nextToken();

      // if the URL starts with a disallowed path, it is not safe
      if (strURL.indexOf(strBadPath) == 0)
        return false;
    }

    return true;
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
    crawler.startCrawl();
  }

}
