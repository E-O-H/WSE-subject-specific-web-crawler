import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.PriorityQueue;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
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
  
  @Option(name = "-DEBUG", aliases = "-D", required = false, 
      usage = "Use this flag to print out even more debug details.")
  private boolean DEBUG = false;
  
  @Option(name = "-ROBOTSAFE_DEBUG", aliases = "-RD", required = false, 
      usage = "Use this flag to print out disallowed paths in robots.txt.")
  private boolean ROBOTSAFE_DEBUG = false;
  
  @Option(name = "-token-delim", aliases = "-td", required = false, 
      usage = "Tokenization delimiters. "
               + "Default is \" \\t\\n\\r\\f,./<>?;:'\\\"[]{}\\|`~!@#$%^&*()_+-=\".")
  private String TOKEN_DELIM = " \t\n\r\f,./<>?;:'\"[]{}\\|`~!@#$%^&*()_+-=";
  
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
      } else if (! url.equals(other.url))
        return false;
      return true;
    }
  }
  
  /**
   * Get all nodes in a Jsoup DOM document (or other element) recursively in DFS order.
   * 
   * (Jsoup only provides methods to get all element nodes with 
   * .getAllElements() or .select("*"); I wrote this method to get all
   * nodes including element nodes AND text nodes.)
   * 
   * @param root document (or other element as the root)
   * @return list of all nodes
   */
  static List<Node> getAllNodesDFS(Node root) {
    List<Node> ret = new ArrayList<Node>();
    ret.add(root);
    List<Node> children = root.childNodes();
    for (int i = 0; i < children.size(); ++i) {
      ret.addAll(getAllNodesDFS(children.get(i)));
    }
    return ret;
  }
  
  /**
   * Calculate the score of a link with respect to a list of given query terms
   * as well as the page context the URL is in.
   * 
   * @param link the DOM element node of the link
   * @param page the page where the URL resides
   * @param queryTerms list of query terms to score the link
   * @return the score
   */
  public int scoreLink(Element link, Document page, String[] queryTerms) {
    final int CONTEXT_SIZE = 5;      // Number of words around the link to check for context score
    
    final int ANCHOR_SCORE = 50;     // anchor score coefficient
    final int URL_SCORE = 40;        // URL score coefficient
    final int CONTEXT_SCORE = 4;     // context score coefficient
    final int PAGE_SCORE = 1;        // page score coefficient
    
    if (queryTerms == null || queryTerms.length == 0) 
      return 0;
    
    // Normalize query terms to lower case
    for (int i = 0; i < queryTerms.length; ++i) {
      queryTerms[i] = queryTerms[i].toLowerCase();
    }
    
    String url = link.attr("href").toLowerCase();  // Get the href attribute of the tag (i.e. the URL)
    String anchor = link.text().toLowerCase();     // Get the text of the tag (i.e. the anchor text)
    
    // Check for anchor score
    int count = 0;
    for (String term : queryTerms) {
      if(anchor.contains(term)) {
        if (DEBUG) System.out.println(
            "Found query term \"" + term + "\" in link anchor text.");
        count += 1;
      }
    }
    if (count > 0) {
      return count * ANCHOR_SCORE;
    }
    
    // Check for URL score
    for (String term : queryTerms) {
      if(url.contains(term)) {
        if (DEBUG) System.out.println(
            "Found query term \"" + term + "\" in link URL.");
        return URL_SCORE;
      }
    }
    
    List<Node> allNodes = getAllNodesDFS(page);  // Get all DOM nodes in the page
    
    // Find the position of the link node in DOM
    int pos = 0;
    for ( ; pos < allNodes.size(); ++pos) {
      if (allNodes.get(pos).equals(link)) {
        if (DEBUG) System.out.println("Found link at the " + pos + "th DOM node.");
        break;
      }
    }
    
    // Check for context score
    int contextCount = 0;
    LOOP_TERMS:
    for (int i = 0; i < queryTerms.length; ++i) {
      // Skip same terms
      for (int j = 0; j < i; ++j) {
        if (queryTerms[i] == queryTerms[j])
          continue LOOP_TERMS;
      }
      
      // Check for context words after the link
      int distance = 1;
      ListIterator<Node> itForward = allNodes.listIterator(pos);
      if (itForward.hasNext()) itForward.next(); // Skip the link element node itself
      for (int l = 0; l < ((Element) allNodes.get(pos)).textNodes().size(); ++l) {
        // Skip through the text nodes of the link, i.e. the anchor text of the link itself.
        if (itForward.hasNext()) itForward.next(); 
      }
      LOOP_NODES:
      while (itForward.hasNext() && distance <= CONTEXT_SIZE) {
        Node node = itForward.next();
        if (! (node instanceof TextNode)) {
          continue LOOP_NODES;
        }
        StringTokenizer st = new StringTokenizer(((TextNode) node).text().toLowerCase(), TOKEN_DELIM);
        while (st.hasMoreTokens() && distance <= CONTEXT_SIZE) {
          if (queryTerms[i].equals(st.nextToken())) {
            contextCount += 1;
            if (DEBUG) System.out.println(
                "Found query term \"" + queryTerms[i] + 
                "\" at a distance of " + distance + " words after the link.");
            continue LOOP_TERMS;
          }
          distance += 1;
        } // LOOP_WORDS
      } // LOOP_NODES
      
      // Check for context words before the link
      distance = 1;
      ListIterator<Node> itBackward = allNodes.listIterator(pos);
      LOOP_NODES:
      while (itBackward.hasPrevious() && distance <= CONTEXT_SIZE) {
        Node node = itBackward.previous();
        if (! (node instanceof TextNode)) {
          continue LOOP_NODES;
        }
        StringTokenizer st = new StringTokenizer(((TextNode) node).text().toLowerCase(), TOKEN_DELIM);
        List<String> words = new ArrayList<String>();
        while (st.hasMoreTokens()) {
          words.add(st.nextToken());
        }
        for (int k = words.size() - 1; k >= 0 && distance <= CONTEXT_SIZE; --k) {
          if (queryTerms[i].equals(words.get(k))) {
            contextCount += 1;
            if (DEBUG) System.out.println(
                "Found query term \"" + queryTerms[i] + 
                "\" at a distance of " + distance + " words before the link.");
            continue LOOP_TERMS;
          }
          distance += 1;
        } // LOOP_WORDS
      } // LOOP_NODES
    } // LOOP_TERMS
    
    // Check for page score
    int pageCount = 0;
    LOOP_TERMS:
    for (int i = 0; i < queryTerms.length; ++i) {
      // Skip same terms
      for (int j = 0; j < i; ++j) {
        if (queryTerms[i] == queryTerms[j])
          continue LOOP_TERMS;
      }
      
      StringTokenizer st = new StringTokenizer(page.text().toLowerCase(), TOKEN_DELIM);
      while (st.hasMoreTokens()) {
        if (queryTerms[i].equals(st.nextToken())) {
          pageCount += 1;
          if (DEBUG) System.out.println(
              "Found query term \"" + queryTerms[i] + "\" in the page outside of link.");
          continue LOOP_TERMS;
        }
      } // LOOP_WORDS
    } // LOOP_TERMS
    
    return contextCount * (CONTEXT_SCORE - 1) + pageCount * PAGE_SCORE;
  }
  
  private void startCrawl() {
    printDescription();
      
    Queue<ScoredUrl> urlQueue = new PriorityQueue<ScoredUrl>(10, (a, b) -> b.score - a.score);
    Set<String> visited = new HashSet<String>();
    
    // Add starting URL
    urlQueue.offer(new ScoredUrl(startingUrl, 0));
    
    // Crawler loop
    while (! urlQueue.isEmpty() && visited.size() < maxPage) {
      // Retrieve a page from the priority queue
      ScoredUrl scoredUrl = urlQueue.poll();
      
      // Normalize the URL
      httpToHttps(scoredUrl);
      
      // Skip if already visited
      if (visited.contains(scoredUrl.url)) continue;
      
      // Skip URL if it is not allowed to visit by robot
      if (! robotSafe(scoredUrl.url)) continue;
      
      // Download the page
      if (trace) System.out.println("\nDownloading: " + scoredUrl.url + 
                                    ". Score = " + scoredUrl.score);
      Document page = downloadPage(scoredUrl.url, downloadPath);
      if (page == null) continue; // This can happen if the link is invalid (eg. 404), 
                                  // or other network error
      if (trace) System.out.println("Received: " + scoredUrl.url);
      visited.add(scoredUrl.url);
      if (visited.size() >= maxPage) break;
      
      // Get all links in the retrieved page
      Elements links = page.select("a[href]");
      
      links.forEach( link -> {
        String linkUrl = link.attr("abs:href"); // The "abs:" attribute prefix is used to automatically 
                                                // resolve an absolute URL if the link is a relative URL
        if (visited.contains(linkUrl)) return;
        
        // Score the link
        if (DEBUG) System.out.println(" -- Scoring link " + linkUrl + " in the downloaded page.");
        int newScore = scoreLink(link, page, queryTerms);
        
        if (urlQueue.contains(new ScoredUrl(linkUrl, 0))) {
          // URL already exists in queue.
          // Find the existing URL in queue and add to its score.
          // (Note with PriorityQueue, this needs to be implemented by
          // inserting a new element with higher score.)
          int[] previousScore = {0}; // Use wrapper trick to modify local variable in lambda expression
          urlQueue.forEach( existing -> {
            if (linkUrl.equals(existing.url)) {
              previousScore[0] = Math.max(previousScore[0], existing.score);
            }
          } );
          urlQueue.offer(new ScoredUrl(linkUrl, previousScore[0] + newScore));
          if (trace) System.out.println("Adding " + newScore + " to score of " + linkUrl);
        } else {
          // URL not already in queue.
          // Add the URL to queue.
          urlQueue.offer(new ScoredUrl(linkUrl, newScore));
          if (trace) System.out.println("Adding to queue: " + linkUrl + " with score " + newScore);
        }
      } ); // for each link
    } // while-loop (crawler loop)
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
   * Convert http to https.
   * 
   * @param scoredUrl link to convert
   */
  static void httpToHttps(ScoredUrl scoredUrl) {
    String url = scoredUrl.url;
    if (url.substring(0, 7).equals("http://")) {
      scoredUrl.url = "https://" + url.substring(7);
    }
  }
  
  /**
   * Prints the description text.
   */
  void printDescription() {
    System.out.print("Crawling for " + maxPage + " pages relevant to \"");
    for (int i = 0; i < queryTerms.length; ++i) {
      if (i != 0) System.out.print(" ");
      System.out.print(queryTerms[i]);
    }
    System.out.print("\" starting from " + startingUrl);
    System.out.println();
  }
  
  /**
   * Test if a URL allows robot according to the site's "robots.txt" file.
   * 
   * This method is modified from Mr. Davis's code at 
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
    String strRobot = "https://" + strHost + "/robots.txt";
    URL urlRobot;
    try { 
      urlRobot = new URL(strRobot);
    } catch (MalformedURLException e) {
      // something weird is happening, so don't trust it
      return false;
    }

    String strCommands = new String();
    try (InputStream urlRobotStream = urlRobot.openStream()) {
      // read in entire file
      byte b[] = new byte[1000];
      int numRead = urlRobotStream.read(b);
      while (numRead != -1) {
        String newCommands = new String(b, 0, numRead);
        strCommands += newCommands;
        numRead = urlRobotStream.read(b);
      }
    } catch (IOException e) {
        // if there is no robots.txt file, it is OK to search
        return true;
    }

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
      if (ROBOTSAFE_DEBUG) System.out.println(DISALLOW + " " + strBadPath);
      
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
