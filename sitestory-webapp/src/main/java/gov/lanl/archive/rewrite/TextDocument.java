/*
 *  This file is part of the Wayback archival access software
 *   (http://archive-access.sourceforge.net/projects/wayback/).
 *
 *  Licensed to the Internet Archive (IA) by one or more individual 
 *  contributors. 
 *
 *  The IA licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package gov.lanl.archive.rewrite;

import gov.lanl.archive.Memento;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;



/**
 * Class which wraps functionality for converting a Resource(InputStream + 
 * HTTP headers) into a StringBuilder, performing several common URL 
 * resolution methods against that StringBuilder, inserting arbitrary Strings
 * into the page, and then converting the page back to a byte array. 
 *
 * @author luda inspired by brad
 * @version
 */
public class TextDocument {
	// if documents are marked up before sending to clients, the data is
	// decoded into a String in chunks. This is how big a chunk to decode with.
	private final static int C_BUFFER_SIZE = 4096;

	//private Resource resource = null;
	//private CaptureSearchResult result = null; 
	private LinkConverter uriConverter = null;
	/**
	 * the internal StringBuilder
	 */
	public StringBuilder sb = null;
	private String charSet = null;
	private byte[] resultBytes = null;
	String pageUrl = null;
	String captureDate = null;
	int recordLength;
	/**
	 * @param resource
	 * @param result
	 * @param uriConverter 
	 */
	//public TextDocument(Resource resource, CaptureSearchResult result, 
		//	ResultURIConverter uriConverter) {
		//this.resource = resource;
		//this.result = result;
		//this.uriConverter = uriConverter;
	//}
      public TextDocument(Memento resource,String pageUrl,String captureDate,LinkConverter uriConverter ) {
    	  recordLength = (int) resource.getLength();
    	  this.pageUrl = pageUrl;
    	  System.out.println("pageurl:"+pageUrl);
    	  this.captureDate = captureDate;
    	  this.uriConverter = uriConverter;
      }
	
	public void addBase() {

		// TODO: get url from Resource instead of SearchResult?
		//String pageUrl = result.getOriginalUrl();
		//String captureDate = result.getCaptureTimestamp();

		String existingBaseHref = TagParser.getBaseHref(sb);
		if (existingBaseHref == null) {
			insertAtStartOfHead("<base href=\"" + pageUrl + "\" />");
		} else {
			pageUrl = existingBaseHref;
		}
   }

	/**
	 * Update URLs inside the page, so those URLs which must be correct at
	 * page load time resolve correctly to absolute URLs.
	 * 
	 * This means ensuring there is a BASE HREF tag, adding one if missing,
	 * and then resolving:
	 *     FRAME-SRC, META-URL, LINK-HREF, SCRIPT-SRC
	 * tag-attribute pairs against either the existing BASE-HREF, or the
	 * page's absolute URL if it was missing.
	 */
	public void resolvePageUrls() {

		// TODO: get url from Resource instead of SearchResult?
		//String pageUrl = result.getOriginalUrl();
		//String captureDate = result.getCaptureTimestamp();

		String existingBaseHref = TagParser.getBaseHref(sb);
		if (existingBaseHref == null) {
			insertAtStartOfHead("<base href=\"" + pageUrl + "\" />");
		} else {
			pageUrl = existingBaseHref;
		}

		String markups[][] = {
				{"FRAME","SRC"},
				{"META","URL"},
				{"LINK","HREF"},
				{"SCRIPT","SRC"},
				{TagParser.ANY_TAGNAME,"background"}
		};
		// TODO: The classic WM added a js_ to the datespec, so NotInArchives
		// can return an valid javascript doc, and not cause Javascript errors.
		for(String tagAttr[] : markups) {
			TagParser.markupTagREURIC(sb, uriConverter, captureDate, pageUrl,
					tagAttr[0], tagAttr[1]);
		}
		TagParser.markupCSSImports(sb,uriConverter, captureDate, pageUrl);
		TagParser.markupStyleUrls(sb,uriConverter,captureDate,pageUrl);
	}
	
	/**
	 * Update all URLs inside the page, so they resolve correctly to absolute 
	 * URLs within the Wayback service.
	 */
	public void resolveAllPageUrls() {

		// TODO: get url from Resource instead of SearchResult?
		//String pageUrl = result.getOriginalUrl();
		//String captureDate = result.getCaptureTimestamp();

		String existingBaseHref = TagParser.getBaseHref(sb);
		if (existingBaseHref != null) {
			pageUrl = existingBaseHref;
		}
		LinkConverter ruc = new SpecialResultURIConverter(uriConverter);
		
		// TODO: forms...?
		String markups[][] = {
				{"FRAME","SRC"},
				{"META","URL"},
				{"LINK","HREF"},
				{"SCRIPT","SRC"},
				{"IMG","SRC"},
				{"A","HREF"},
				{"AREA","HREF"},
				{"OBJECT","CODEBASE"},
				{"OBJECT","CDATA"},
				{"APPLET","CODEBASE"},
				{"APPLET","ARCHIVE"},
				{"EMBED","SRC"},
				{"IFRAME","SRC"},
				{TagParser.ANY_TAGNAME,"background"}
		};
		for(String tagAttr[] : markups) {
			TagParser.markupTagREURIC(sb, ruc, captureDate, pageUrl,
					tagAttr[0], tagAttr[1]);
		}
		TagParser.markupCSSImports(sb,uriConverter, captureDate, pageUrl);
		TagParser.markupStyleUrls(sb,uriConverter,captureDate,pageUrl);
	}
	
	public void resolveCSSUrls() {
		// TODO: get url from Resource instead of SearchResult?
		//String pageUrl = result.getOriginalUrl();
		//String captureDate = result.getCaptureTimestamp();
		TagParser.markupCSSImports(sb,uriConverter, captureDate, pageUrl);
	}

	public void resolveASXRefUrls() {

		// TODO: get url from Resource instead of SearchResult?
		//String pageUrl = result.getOriginalUrl();
		//String captureDate = result.getCaptureTimestamp();
		  LinkConverter ruc = new MMSToHTTPResultURIConverter(uriConverter);
		
		TagParser.markupTagREURIC(sb, ruc, captureDate, pageUrl,
				"REF", "HREF");
	}
	
	public void stripHTML() {
		String stripped = sb.toString().replaceAll("\\<.*?>","");
		sb.setLength(0);
		sb.append(stripped);
	}
	/**
	 * @param charSet
	 * @throws IOException 
	 */
/*	public void readFully(String charSet) throws IOException {
		this.charSet = charSet;
//		int recordLength = (int) resource.getRecordLength();

		// convert bytes to characters for charset:
		InputStreamReader isr = new InputStreamReader(resource, charSet);

		char[] cbuffer = new char[C_BUFFER_SIZE];

		// slurp the whole thing into RAM:
		sb = new StringBuilder(recordLength);
		for (int r = -1; (r = isr.read(cbuffer, 0, C_BUFFER_SIZE)) != -1;) {
			sb.append(cbuffer, 0, r);
		}
	}
	*/
	
	public void readFully(InputStreamReader isr) throws IOException {
		//this.charSet = charSet;
//		int recordLength = (int) resource.getRecordLength();

		// convert bytes to characters for charset:
		//InputStreamReader isr = new InputStreamReader(resource, charSet);

		char[] cbuffer = new char[C_BUFFER_SIZE];

		// slurp the whole thing into RAM:
		sb = new StringBuilder(recordLength);
		for (int r = -1; (r = isr.read(cbuffer, 0, C_BUFFER_SIZE)) != -1;) {
			sb.append(cbuffer, 0, r);
		}
		//System.out.println("readfully:"+sb.toString());
	}
	
	
	
	/**
	 * Read bytes from input stream, using best-guess for character encoding
	 * @throws IOException 
	 */
	//public void readFully() throws IOException {
		//readFully(null);
	//}
	
	/**
	 * @return raw bytes contained in internal StringBuilder
	 * @throws UnsupportedEncodingException
	 */
	public byte[] getBytes() throws UnsupportedEncodingException {
		if(resultBytes != null) {
			return resultBytes;
		}
		if(sb == null) {
			throw new IllegalStateException("No interal StringBuffer");
		}
		if(resultBytes == null) {
			System.out.println(sb.toString());
			
			resultBytes = sb.toString().getBytes(charSet);
		}
		return resultBytes;
	}
	public String getResult() throws UnsupportedEncodingException {		
		if(sb == null) {
			throw new IllegalStateException("No interal StringBuffer");
		}
		return sb.toString();
	}
	
	public void setResultBytes(byte[] resultBytes) {
		this.resultBytes = resultBytes;
	}
	
	/**
	 * Write the contents of the page to the client.
	 * 
	 * @param os
	 * @throws IOException
	 */
	public void writeToOutputStream(OutputStream os) throws IOException {
		if(sb == null) {
			throw new IllegalStateException("No interal StringBuffer");
		}
		byte[] b;
		try {
			b = getBytes();
		} catch (UnsupportedEncodingException e) {
			throw new RuntimeException(e);
		}
		os.write(b);
	}

	/**
	 * @param toInsert
	 */	
	public void insertAtStartOfDocument(String toInsert) {
		sb.insert(0,toInsert);
	}

	/**
	 * @param toInsert
	 */	
	public void insertAtStartOfHead(String toInsert) {
		int insertPoint = TagParser.getEndOfFirstTag(sb,"head");
		if (-1 == insertPoint) {
			insertPoint = 0;
		}
		sb.insert(insertPoint,toInsert);
	}

	/**
	 * @param toInsert
	 */
	public void insertAtEndOfBody(String toInsert) {
		int insertPoint = sb.lastIndexOf("</body>");
		if (-1 == insertPoint) {
			insertPoint = sb.lastIndexOf("</BODY>");
		}
		if (-1 == insertPoint) {
			insertPoint = sb.length();
		}
		sb.insert(insertPoint,toInsert);
	}
	/**
	 * @param toInsert
	 */
	public void insertAtStartOfBody(String toInsert) {
		int insertPoint = TagParser.getEndOfFirstTag(sb,"body");
		if (-1 == insertPoint) {
			insertPoint = 0;
		}
		sb.insert(insertPoint,toInsert);
	}	
	/**
	 * @param jspPath
	 * @param httpRequest
	 * @param httpResponse
	 * @param wbRequest
	 * @param results
	 * @return
	 * @throws IOException 
	 * @throws ServletException 
	 * @throws ParseException 
	 */
/*	public String includeJspString(String jspPath, 
			HttpServletRequest httpRequest, HttpServletResponse httpResponse,
			WaybackRequest wbRequest, CaptureSearchResults results, 
			CaptureSearchResult result, Resource resource) 
	throws ServletException, IOException {
		
		UIResults uiResults = new UIResults(wbRequest,uriConverter,results,
				result,resource);

		StringHttpServletResponseWrapper wrappedResponse = 
			new StringHttpServletResponseWrapper(httpResponse);
		uiResults.forward(httpRequest, wrappedResponse, jspPath);
		return wrappedResponse.getStringResponse();
	}
	*/
	/**
	 * @param jsUrl
	 * @return
	 */
	public String getJSIncludeString(final String jsUrl) {
		return "<script type=\"text/javascript\" src=\"" 
			+ jsUrl + "\" ></script>\n";
	}

	/**
	 * @return the charSet
	 */
	public String getCharSet() {
		return charSet;
	}

	/**
	 * @param charSet the charSet to set
	 */
	public void setCharSet(String charSet) {
		this.charSet = charSet;
	}

	private class SpecialResultURIConverter implements LinkConverter {
		private static final String EMAIL_PROTOCOL_PREFIX = "mailto:";
		private static final String JAVASCRIPT_PROTOCOL_PREFIX = "javascript:";
		private LinkConverter base = null;
		public SpecialResultURIConverter(LinkConverter base) {
			this.base = base;
		}
		public String makeReplayURI(String datespec, String url) {
			System.out.println("url from SpecialResultURIConverter:" +url);
			if(url.startsWith(EMAIL_PROTOCOL_PREFIX)) {
				return url;
			}
			if(url.startsWith(JAVASCRIPT_PROTOCOL_PREFIX)) {
				return url;
			}
			return base.makeReplayURI(datespec, url);
		}
	}

	private class MMSToHTTPResultURIConverter implements LinkConverter {
		private static final String MMS_PROTOCOL_PREFIX = "mms://";
		private static final String HTTP_PROTOCOL_PREFIX = "http://";
		private LinkConverter base = null;
		public MMSToHTTPResultURIConverter(LinkConverter base) {
			this.base = base;
		}
		public String makeReplayURI(String datespec, String url) {
			System.out.println("url from MMSToHTTPResultURIConverter:" +url);
			if(url.startsWith(MMS_PROTOCOL_PREFIX)) {
				url = HTTP_PROTOCOL_PREFIX + 
					url.substring(MMS_PROTOCOL_PREFIX.length());
			}
			return base.makeReplayURI(datespec, url);
		}
	}	
}
