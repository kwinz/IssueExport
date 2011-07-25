import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.codehaus.jackson.*;
import org.codehaus.jackson.map.*;

public class StartIssueExporter {

    // input
    private static final String user = "kwinz";
    private static final String password = "???";
    private static final String repo = "IndoorsViewer";

    // const
    private static final String CONST_URI = "https://api.github.com/repos/" + user + '/' + repo
	    + "/issues";
    private static final String AUTHENTICATION = new sun.misc.BASE64Encoder()
	    .encode((user + ':' + password).getBytes());

    // output
    public static final char CSV = ';';
    public static final String OUT_FILE = "/media/tmpfs/lol/" + repo + ".csv";
    

    public static void main(final String... args) {

	
	System.err.println(CONST_URI);
	System.err.flush();
	
	
	try {
	    PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(OUT_FILE)));
//	    PrintWriter out = new PrintWriter(
//		    new BufferedWriter(new OutputStreamWriter(System.out)));

	    out.append("number" + CSV + "title" + CSV + "state" + CSV + "created" + CSV + "labels"
		    + CSV + "comments" + '\n');
	    out.flush();

	    int pages = getNumPages(false);
	    for (int page = 1; page <= pages; page++) {
		process(page, false, out);
	    }
	    
	    pages = getNumPages(true);
	    for (int page = 1; page <= pages; page++) {
		process(page, true, out);
	    }

	    out.close();
	} catch (JsonParseException e) {
	    e.printStackTrace();
	} catch (IOException e) {
	    e.printStackTrace();
	}
	
    }

    /**
     * 
     * @return number of pages
     */
    static int getNumPages(boolean closed) {
	HttpURLConnection urlc = null;
	InputStream is = null;

	int pages = -1;

	try {
	    int response = 0;

	    urlc = (HttpURLConnection) (new URL(CONST_URI+"?state="+(closed?"closed":"open")).openConnection());
	    urlc.setRequestMethod("HEAD");
	    urlc.setRequestProperty("Authorization", "Basic " + AUTHENTICATION);
	    urlc.connect();

	    Map<String, List<String>> headerFields = urlc.getHeaderFields();
	    List<String> list = headerFields.get("Link");
	    
	    //if there is no Link header assume there is only one page.
	    if (list ==null || list.size() == 0)
		return 1;

	    response = urlc.getResponseCode();
	    if (response != 200)
		throw new IOException("HTTP response was NOT OK(200) !");

	    String lastLink = list.get(list.size() - 1);

	    System.err.println(lastLink);

	    //get total number of pages from link to last page
	    pages = Integer.parseInt(lastLink.substring(lastLink.lastIndexOf('?') + 6,
		    lastLink.lastIndexOf('&')));

	} catch (IOException e) {
	    throw new RuntimeException("An error occured while reading number of pages.",e);
	} finally {
	    if (is != null)
		try {
		    is.close();
		} catch (final IOException e) {
		    e.printStackTrace();
		}
	}
	
	//github sometimes returns 0 if there is really 1 page
	pages=pages==0?1:pages;

	return pages;
    }

    static void process(int page, boolean closed, PrintWriter out) throws JsonParseException, IOException {

	HttpURLConnection urlc = null;
	InputStream is = null;

	try {

	    int response = 0;

	    urlc = (HttpURLConnection) (new URL(CONST_URI +"?state="+(closed?"closed":"open") +"&page=" + page).openConnection());

	    urlc.setRequestProperty("Authorization", "Basic " + AUTHENTICATION);
	    urlc.setRequestProperty("Accept-Encoding", "gzip");
	    urlc.connect();

	    // Map<String, List<String>> headerFields = urlc.getHeaderFields();
	    // List<String> list = headerFields.get("Link");
	    // for (String header : list) {
	    // System.out.println(header);
	    // }

	    response = urlc.getResponseCode();
	    if (response != 200)
		throw new IOException("HTTP response was NOT OK(200) !");

	    String contentEncoding = urlc.getContentEncoding();
	    is = urlc.getInputStream();

	    if (contentEncoding != null && contentEncoding.equalsIgnoreCase("gzip")) {
		// System.err.println("Using gzip compression.");
		is = new GZIPInputStream(is);
	    }

	    // BufferedReader br = new BufferedReader(new
	    // InputStreamReader(is));
	    // while (br.ready()) {
	    // System.out.println(br.readLine());
	    // }

	    writeCSV(is, out);

	} catch (IOException e) {
	    e.printStackTrace();
	} finally {
	    // now that we are done,
	    // try closing our connections
	    if (is != null)
		try {
		    is.close();
		} catch (IOException e) {
		    e.printStackTrace();
		}
	}
    }

    public static void writeCSV(InputStream is, PrintWriter out) throws JsonParseException,
	    JsonMappingException, IOException {

	ObjectMapper m = new ObjectMapper();
	JsonNode rootNode = m.readValue(is, JsonNode.class);

	final Iterator<JsonNode> issues = rootNode.getElements();
	while (issues.hasNext()) {
	    JsonNode issue = issues.next();
	    final String url = issue.get("url").getTextValue();

	    String labelString = "";
	    Iterator<JsonNode> labels = issue.get("labels").getElements();
	    while (labels.hasNext()) {
		JsonNode label = labels.next();
		String labelUrl = label.get("url").getTextValue();
		labelString += labelUrl.subSequence(labelUrl.lastIndexOf('/') + 1,
			labelUrl.length()).toString();
		if (labels.hasNext())
		    labelString += '+';
	    }

	    out.append(url.subSequence(url.lastIndexOf('/') + 1, url.length()).toString() + CSV
		    + issue.get("title").getTextValue() + CSV + issue.get("state").getTextValue()
		    + CSV + issue.get("created_at").getTextValue() + CSV + labelString + CSV
		    + issue.get("comments").getIntValue() + '\n');

	}

    }

    @Deprecated
    static void processStream() throws JsonParseException, IOException {
	JsonFactory f = new JsonFactory();
	JsonParser jp = f.createJsonParser(new File("/media/tmpfs/measurement.txt"));

	// PrintWriter out = new PrintWriter(new BufferedWriter(new
	// FileWriter("/media/tmpfs/foo.csv")));
	PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out)));

	jp.nextToken();

	while (jp.hasCurrentToken()) {

	    String created = null, state = null, number = null, labels = null, comments = null, title = null;

	    String fieldname = jp.getCurrentName();
	    jp.nextToken();

	    if ("created_at".equals(fieldname)) {
		String url = jp.getCurrentName();
		String value = jp.getText();

	    } else if ("state".equals(fieldname)) {

	    } else if ("url".equals(fieldname)) {
		number = jp.getText();

	    } else if ("labels".equals(fieldname)) {

	    } else if ("comments".equals(fieldname)) {

		comments = jp.getText();

		out.append(number + ',' + title + ',' + state + ',' + created + ',' + labels + ','
			+ comments + '\n');
	    }

	    out.flush();
	}

    }

}
