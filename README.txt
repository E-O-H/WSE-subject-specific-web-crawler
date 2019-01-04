Usage: 
java -cp ".;bin;D:/myJavaWorkspace/args4j-2.33.jar;D:/myJavaWorkspace/jsoup-1.11.3/jsoup-1.11.3.jar;" Crawler -u STARTING_URL -docs DOWNLOAD_PATH  [-q QUERY_TERMS]  [-m MAX_PAGES] [-t] [-D] [-RD] [-td TOKENIZATION_DELIMITERS] [-h] 

If no query term is given, all links are treated equally. Multiple-word query does not need to be quoted.

Downloaded files will be saved in DOWNLOAD_PATH in their original directory structure; pages with URLs that represent a directory will be written as "index.html" in their corresponding directories. Downloaded files will retain their original capitalization, but query and score is case-insensitive.

Illegal characters in filenames will be changed or omitted depending on the character. For example, < and > are changed to [ and ] respectively. *?"| are all changed to underscore, while colons are omitted. 

All URL is normalized to HTTPS protocol (along with some other canonicalization).

I used java.util.PriorityQueue, which does not guarentee the original order for tied elements. Therefore links with the same score are not retrieved in the same order as they are offered into the queue. This causes some differences in the output from the given sample.

Some instructions regarding the extra options:
Use -t to print a trace (mainly link scores and downloaded page)
Use -D (-DEBUG) to print out some more detailed information (mainly link scoring details, such as the position of every link in every page, distance of a matched query term from a link in the page, etc.)
Use -RD (-ROBOTSAFE_DEBUG) to print out the disallowed paths in robots.txt
Use -td (-token-delim) to set tokenization delimiters. Default is " \t\n\r\f,./<>?;:'\"[]{}\\|`~!@#$%^&*()_+-=".
Use -h (-help) to print usage.