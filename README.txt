Usage: 
java -cp ".;bin;D:/myJavaWorkspace/args4j-2.33.jar;D:/myJavaWorkspace/jsoup-1.11.3/jsoup-1.11.3.jar;" Crawler -u STARTING_URL -q QUERY_TERMS -docs DOWNLOAD_PATH [-m MAX_PAGES] [-t]

Multiple-word query does not need to be quoted.

Downloaded files will be saved in DOWNLOAD_PATH in their original directory structure; pages with URLs that represent a directory will be written as "index.html" in their corresponding directories.

Illegal characters in filenames will be changed or omitted depending on the character. For example, < and > are changed to [ and ] respectively. *?"| are all changed to underscore, while colons are omitted. 

Use -h (-help) to print usage.